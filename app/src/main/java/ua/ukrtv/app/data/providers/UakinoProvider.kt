package ua.ukrtv.app.data.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.repository.CatalogRepository
import ua.ukrtv.app.data.repository.SessionRepository
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.SearchScorer

class UakinoProvider(
    htmlHttpClient: HtmlHttpClient,
    sessionRepository: SessionRepository,
    catalogRepository: CatalogRepository
) : DleProviderBase(htmlHttpClient, sessionRepository, catalogRepository, UakinoProfile) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val name: String = "Uakino"
    override val baseUrl: String = "https://uakino.best/"
    override val brandColor: String = "#ca563f"

    override fun supportsUrl(url: String) = url.contains("uakino.best")

    override suspend fun search(query: String, limit: Int): List<SearchItem> = withContext(Dispatchers.IO) {
        val q = query.trim().takeIf { it.isNotEmpty() } ?: return@withContext emptyList()

        if (catalogRepository.isProviderReady(name)) {
            val results = catalogRepository.searchByProvider(name, q, limit)
            if (results.isNotEmpty()) {
                return@withContext results.map { SearchItem(it.title, it.url, it.poster, name) }
            }
        }

        val allResults = performDleSearch(q, limit)
        val filtered = allResults.map { SearchItem(it.title, it.pageUrl, it.poster, name) }

        if (filtered.isNotEmpty()) return@withContext filtered
        return@withContext searchCatalogFallback(q, limit)
    }

    private suspend fun searchCatalogFallback(query: String, limit: Int): List<SearchItem> = withContext(Dispatchers.IO) {
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

    override suspend fun getMediaSource(pageUrl: String, season: Int?, episode: Int?, isDeep: Boolean, prefetchedHtml: String?): MediaSource? = withContext(Dispatchers.IO) {
        if (sessionUserHash.isEmpty()) initializeSession()
        val html = prefetchedHtml ?: pageHtmlCache.get(pageUrl).also { pageHtmlCache.invalidate(pageUrl) } ?: run {
            htmlHttpClient.getHtml(pageUrl, baseUrl)
                ?: throw java.io.IOException("Не вдалося завантажити сторінку: $pageUrl")
        }

        val doc = Jsoup.parse(html, pageUrl)
        
        // Try AJAX first (it works for both movies and series on Uakino now)
        var source = resolveSeriesAjax(doc, pageUrl)
        
        // Fallback to iframe/direct extraction, reuse existing doc
        if (source == null) {
            source = resolveMovieFromPage(html, pageUrl, doc)
        }

        return@withContext DleResolutionUtils.promoteToSeriesIfNeeded(source, pageUrl, name)
    }

    private suspend fun resolveMovieFromPage(html: String, pageUrl: String, doc: Document? = null): MediaSource? {
        val directUrls = DleResolutionUtils.findMediaUrlsInText(html)
        if (directUrls.isNotEmpty()) {
            val best = selectBestMediaUrl(directUrls) ?: directUrls.first()
            return MediaSource.Movie(best, directUrls.filter { it != best }, pageUrl, name)
        }

        val parsedDoc = doc ?: Jsoup.parse(html, pageUrl)
        val iframes = parsedDoc.select("iframe").mapNotNull { ifr ->
            val src = ifr.attr("abs:data-src").ifEmpty { ifr.attr("abs:src") }
            if (src.isEmpty() || src.contains("youtube") || src.contains("facebook")) null else src
        }

        for (src in iframes.take(2)) {
            try {
                val iframeResp = htmlHttpClient.getHtml(src, pageUrl) ?: continue
                val media = DleResolutionUtils.findMediaUrlsInText(iframeResp)
                if (media.isNotEmpty()) {
                    val best = selectBestMediaUrl(media) ?: media.first()
                    val fallbacks = media.filter { it != best }
                    return MediaSource.Movie(best, fallbacks, pageUrl, name)
                }
            } catch (e: Exception) {
                AppLogger.w("$name:MovieIframe", "Iframe failed: ${e.message}")
            }

            if (src.contains("ashdi") || src.contains("vidmoly") || src.contains("mcloud")) {
                return MediaSource.Movie(src, emptyList(), pageUrl, name)
            }
        }
        return null
    }

    private fun selectBestMediaUrl(media: List<String>): String? {
        if (media.isEmpty()) return null
        if (media.size > 1) return DleResolutionUtils.pickBestQuality(media) ?: media.first()
        return media.first()
    }

    private suspend fun resolveSeriesAjax(doc: org.jsoup.nodes.Document, pageUrl: String): MediaSource? {
        val playlistDiv = doc.selectFirst(".playlists-ajax, [data-news_id]") ?: return null
        val newsId = playlistDiv.attr("data-news_id")
        if (newsId.isBlank()) return null

        suspend fun fetchSeasonEpisodes(seasonUrl: String, seasonNum: Int): ProviderSeason? {
            val sHtml = try { htmlHttpClient.getHtml(seasonUrl, pageUrl) } catch (e: Exception) {
                AppLogger.w("$name:AjaxSeason", "Failed to fetch S$seasonNum: ${e.message}")
                return null
            } ?: return null
            val sDoc = Jsoup.parseBodyFragment(sHtml)
            val sPlaylistDiv = sDoc.selectFirst(".playlists-ajax, [data-news_id]") ?: return null
            val sNewsId = sPlaylistDiv.attr("data-news_id")
            if (sNewsId.isBlank()) return null
            val ajaxData = fetchAjaxPlaylist(sNewsId, seasonUrl) ?: return null
            return ProviderSeason(seasonNum, ajaxData.first, voiceoverOptions = ajaxData.second)
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
        if (otherSeasons.isNotEmpty()) {
            val seasonSemaphore = Semaphore(3) // Increase parallel limit for speed
            coroutineScope {
                otherSeasons.filter { (sNum, _) -> allSeasons.none { it.number == sNum } }
                    .map { (sNum, sUrl) ->
                        async(Dispatchers.IO) {
                            seasonSemaphore.withPermit {
                                try { fetchSeasonEpisodes(sUrl, sNum) } catch (e: Exception) {
                                    AppLogger.w("$name:AjaxSeasons", "Failed S$sNum: ${e.message}")
                                    null
                                }
                            }
                        }
                    }.awaitAll().filterNotNull().forEach { allSeasons.add(it) }
            }
        }

        if (allSeasons.isEmpty()) return null
        val merged = allSeasons.groupBy { it.number }.map { (num, list) ->
            val allEps = list.flatMap { it.episodes }.distinctBy { it.url }.sortedBy { it.number }
            val allVos = list.flatMap { it.voiceoverOptions }.distinct().sorted()
            ProviderSeason(num, allEps, voiceoverOptions = allVos)
        }.sortedBy { it.number }
        return MediaSource.Series(merged, pageUrl, name)
    }

    private suspend fun fetchAjaxPlaylist(newsId: String, referer: String): Pair<List<ProviderEpisode>, List<String>>? {
        val ajaxUrl = "${baseUrl}engine/ajax/playlists.php"
        val body = FormBody.Builder()
            .add("news_id", newsId)
            .add("xfield", "playlist")
            .build()
        val response = try { htmlHttpClient.postHtml(ajaxUrl, body, referer, isAjax = true) } catch (e: Exception) {
            AppLogger.w("$name:AjaxPost", "POST failed: ${e.message}")
            null
        } ?: return null
        val jsonObj = try { json.decodeFromString<JsonObject>(response) } catch (e: Exception) {
            AppLogger.w("$name:AjaxJson", "JSON parse failed: ${e.message}")
            null
        }
        if (jsonObj?.get("success")?.jsonPrimitive?.booleanOrNull != true) return null
        val responseHtml = jsonObj["response"]?.jsonPrimitive?.content ?: return null
        return SeriesPlaylistParser.parseAjaxPlaylistHtml(responseHtml)
    }
}
