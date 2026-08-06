package ua.ukrtv.app.data.providers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.repository.CatalogRepository
import ua.ukrtv.app.data.repository.SessionRepository
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.matching.SearchScorer

class UakinoProvider(
    htmlHttpClient: HtmlHttpClient,
    sessionRepository: SessionRepository,
    catalogRepository: CatalogRepository
) : DleProviderBase(htmlHttpClient, sessionRepository, catalogRepository, UakinoProfile) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val seasonCache = ua.ukrtv.app.data.TtlLruCache<String, ProviderSeason>(
        maxSize = 100,
        ttlMs = Constants.STREAM_RESOLUTION_CACHE_TTL_MS
    )
    private val ajaxPlaylistCache = ua.ukrtv.app.data.TtlLruCache<String, Pair<List<ProviderEpisode>, List<String>>>(
        maxSize = 50,
        ttlMs = Constants.STREAM_RESOLUTION_CACHE_TTL_MS
    )

    override val name: String = "Uakino"
    override val baseUrl: String = "https://uakino.best/"
    override val brandColor: String = "#ca563f"

    override fun supportsUrl(url: String) = url.contains("uakino.best")

    override suspend fun searchFallback(query: String, limit: Int): List<SearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Movie>()
        val q = SearchScorer.transliterate(SearchScorer.normalizeTitle(query))

        for (page in 0 until 3) {
            if (results.size >= limit) break
            val pageUrl = if (page == 0) "/filmy/online/"
                          else "/filmy/online/page/$page/"
            try {
                htmlHttpClient.getHtml(absoluteUrl(pageUrl), baseUrl)?.let { html ->
                    val parsed = parser.parseListFastJsoup(html, baseUrl)
                    if (parsed.isEmpty()) {
                        results.addAll(DleParser.parseListFastRegex(html, baseUrl))
                    } else {
                        results.addAll(parsed)
                    }
                }
            } catch (_: Exception) { break }
        }

        val matched = results.filter { movie ->
            val movieNorm = SearchScorer.transliterate(SearchScorer.normalizeTitle(movie.title))
            movieNorm.contains(q) || q.contains(movieNorm) ||
            SearchScorer.bigramSimilarity(q, movieNorm) > 0.4f
        }.distinctBy { it.pageUrl }.take(limit)

        matched.map { SearchItem(it.title, it.pageUrl, it.poster, name) }
    }

    override suspend fun resolveSeriesContent(
        html: String, pageUrl: String, doc: org.jsoup.nodes.Document,
        season: Int?, episode: Int?, isDeep: Boolean
    ): MediaSource? {
        val playlistDiv = doc.selectFirst(".playlists-ajax, [data-news_id]") ?: return null
        val newsId = playlistDiv.attr("data-news_id")
        if (newsId.isBlank()) return null

        suspend fun fetchSeasonEpisodes(seasonUrl: String, seasonNum: Int): ProviderSeason? =
            withTimeoutOrNull(Constants.PER_SEASON_FETCH_TIMEOUT_MS) {
                val cacheKey = "season|$seasonUrl|$seasonNum"
                seasonCache.get(cacheKey)?.let { return@withTimeoutOrNull it }

                val sHtml = try {
                    htmlHttpClient.getHtml(seasonUrl, pageUrl, skipRateLimitRetry = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w("$name:AjaxSeason", "Failed to fetch S$seasonNum: ${e.message}")
                    null
                } ?: return@withTimeoutOrNull null
                val sDoc = Jsoup.parseBodyFragment(sHtml)
                val sPlaylistDiv = sDoc.selectFirst(".playlists-ajax, [data-news_id]") ?: return@withTimeoutOrNull null
                val sNewsId = sPlaylistDiv.attr("data-news_id")
                if (sNewsId.isBlank()) return@withTimeoutOrNull null
                val ajaxData = fetchAjaxPlaylist(sNewsId, seasonUrl, skipRateLimitRetry = true) ?: return@withTimeoutOrNull null
                ProviderSeason(seasonNum, ajaxData.first, voiceoverOptions = ajaxData.second).also {
                    seasonCache.put(cacheKey, it)
                }
            }

        val ajaxData = fetchAjaxPlaylist(newsId, pageUrl) ?: return null
        val (curEps, cleanVoiceoverNames) = ajaxData

        val allSeasons = mutableListOf<ProviderSeason>()
        val currentSeasonNum = DleResolutionUtils.extractSeasonNum(pageUrl) ?: DleResolutionUtils.extractSeasonNum(doc.title()) ?: 1
        if (curEps.isNotEmpty()) {
            allSeasons.add(ProviderSeason(currentSeasonNum, curEps, voiceoverOptions = cleanVoiceoverNames))
        }

        val otherSeasons = resolveOtherSeasons(doc, pageUrl)
        AppLogger.d("$name:AjaxSeasons", "Found ${otherSeasons.size} other seasons (current=$currentSeasonNum), total goal: ${otherSeasons.size + 1}")

        // When a concrete season is requested (play/prewarm path) only that season's page is
        // needed — fetching all seasons for a 20+ season series is what trips the 429 storm.
        // Full season-structure discovery is a deep-resolution concern; shallow calls must never
        // fan out to every season, otherwise countdown/prefetch paths re-POST the same
        // playlists.php repeatedly (see log: deep=false chains re-fetching all seasons).
        val targetSeasons = when {
            isDeep -> otherSeasons
            season != null -> otherSeasons.filter { it.first == season }
            else -> emptyList()
        }

        if (targetSeasons.isNotEmpty()) {
            val seasonSemaphore = Semaphore(3) // Increase parallel limit for speed
            coroutineScope {
                targetSeasons.filter { (sNum, _) -> allSeasons.none { it.number == sNum } }
                    .mapIndexed { idx, (sNum, sUrl) ->
                        async(Dispatchers.IO) {
                            if (idx > 0) delay(Constants.SERIES_FETCH_STAGGER_MS * idx)
                            seasonSemaphore.withPermit {
                                try { fetchSeasonEpisodes(sUrl, sNum) } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    AppLogger.w("$name:AjaxSeasons", "Failed S$sNum: ${e.message}")
                                    null
                                }
                            }
                        }
                    }.awaitAll().filterNotNull().forEach { allSeasons.add(it) }
            }
        }

        if (allSeasons.isEmpty()) return null
        return mergeSeasons(allSeasons, pageUrl)
    }

    override fun clearCache(url: String?) {
        super.clearCache(url)
        if (url == null) {
            seasonCache.clear()
            ajaxPlaylistCache.clear()
        }
    }

    private suspend fun fetchAjaxPlaylist(
        newsId: String,
        referer: String,
        skipRateLimitRetry: Boolean = false
    ): Pair<List<ProviderEpisode>, List<String>>? {
        val cacheKey = "ajax|$newsId"
        ajaxPlaylistCache.get(cacheKey)?.let { return it }

        val ajaxUrl = "${baseUrl}engine/ajax/playlists.php"
        val body = FormBody.Builder()
            .add("news_id", newsId)
            .add("xfield", "playlist")
            .build()
        val response = try { htmlHttpClient.postHtml(ajaxUrl, body, referer, isAjax = true, skipRateLimitRetry = skipRateLimitRetry) } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("$name:AjaxPost", "POST failed: ${e.message}")
            null
        } ?: return null
        val jsonObj = try { json.decodeFromString<JsonObject>(response) } catch (e: Exception) {
            AppLogger.w("$name:AjaxJson", "JSON parse failed: ${e.message}")
            null
        }
        if (jsonObj?.get("success")?.jsonPrimitive?.booleanOrNull != true) return null
        val responseHtml = jsonObj["response"]?.jsonPrimitive?.content ?: return null
        return SeriesPlaylistParser.parseAjaxPlaylistHtml(responseHtml).also {
            ajaxPlaylistCache.put(cacheKey, it)
        }
    }
}
