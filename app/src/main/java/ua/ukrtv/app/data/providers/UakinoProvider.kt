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
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.repository.SessionRepository

import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor

class UakinoProvider(
    private val htmlHttpClient: HtmlHttpClient,
    private val sessionRepository: SessionRepository
) : MediaProvider {

    private val parser = DleParser(UakinoProfile)
    private val detailCache = TtlLruCache<String, MovieDetail>(50, Constants.METADATA_CACHE_TTL_MS)
    private val pageHtmlCache = TtlLruCache<String, String>(20, 30 * 60 * 1000L)
    private var sessionUserHash: String = ""
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private val SESSION_HASH_REGEX = Regex("""dle_login_hash\s*=\s*['"]([^'"]+)['"]""")
    }

    override val name: String = "Uakino"
    override val baseUrl: String = "https://uakino.best/"
    override val brandColor: String = "#ca563f"

    override fun getHomeCategories(): List<ContentCategory> = UakinoProfile.categoryPaths.keys.toList()
    override fun supportsUrl(url: String) = url.contains("uakino.best")

    override suspend fun initializeSession(): Boolean {
        if (sessionUserHash.isNotEmpty()) return true
        sessionRepository.getSessionHash(name)?.let {
            sessionUserHash = it
            return true
        }
        return withContext(Dispatchers.IO) {
            try {
                val html = htmlHttpClient.getHtml(baseUrl) ?: ""
                sessionUserHash = SESSION_HASH_REGEX.find(html)?.groupValues?.get(1) ?: ""
                if (sessionUserHash.isEmpty()) {
                    val searchHtml = htmlHttpClient.getHtml(absoluteUrl("index.php?do=search")) ?: ""
                    sessionUserHash = SESSION_HASH_REGEX.find(searchHtml)?.groupValues?.get(1) ?: ""
                }
                if (sessionUserHash.isNotEmpty()) {
                    sessionRepository.saveSessionHash(name, sessionUserHash)
                }
                sessionUserHash.isNotEmpty()
            } catch (e: Exception) {
                AppLogger.e(name, "Session init failed", e)
                false
            }
        }
    }

    override suspend fun getHomeSections(page: Int): List<HomeSection> = withContext(Dispatchers.IO) {
        try {
            val html = htmlHttpClient.getHtml(absoluteUrl(if (page > 1) "page/$page/" else "")) ?: return@withContext emptyList()
            val movies = FastHomeParser.parseListOptimized(html, baseUrl, UakinoProfile.selectors)
            if (movies.isNotEmpty()) listOf(HomeSection("Новинки", movies)) else emptyList()
        } catch (e: Exception) {
            AppLogger.w("$name:HomeSections", "Failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getMoviesByCategory(category: ContentCategory, page: Int): List<Movie> = withContext(Dispatchers.IO) {
        val path = UakinoProfile.categoryPaths[category] ?: return@withContext emptyList()
        val fullUrl = absoluteUrl(if (page > 1) "${path}page/$page/" else path)
        try {
            htmlHttpClient.getHtml(fullUrl)?.let { html ->
                val parsed = FastHomeParser.parseListOptimized(html, baseUrl, UakinoProfile.selectors)
                if (parsed.isEmpty() && category == ContentCategory.TRENDS && page == 1) {
                    // Fallback to main page for Trends if specific path fails
                    htmlHttpClient.getHtml(baseUrl)?.let { mainHtml ->
                        FastHomeParser.parseListOptimized(mainHtml, baseUrl)
                    } ?: emptyList()
                } else {
                    parsed
                }
            } ?: emptyList()
        } catch (e: Exception) {
            AppLogger.w("$name:Category", "Failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun search(query: String, limit: Int): List<SearchItem> = withContext(Dispatchers.IO) {
        val q = query.trim().takeIf { it.length >= 3 } ?: return@withContext emptyList()
        if (sessionUserHash.isEmpty()) initializeSession()

        val allResults = mutableListOf<Movie>()
        val body = FormBody.Builder()
            .add("do", "search").add("subaction", "search").add("story", q)
            .apply { if (sessionUserHash.isNotEmpty()) add("user_hash", sessionUserHash) }
            .build()

        try {
            htmlHttpClient.postHtml(absoluteUrl("index.php?do=search"), body, baseUrl)?.let {
                allResults.addAll(parser.parseSearch(it))
            }
        } catch (e: Exception) {
            AppLogger.w("$name:Search", "POST search failed: ${e.message}")
        }

        if (allResults.isEmpty()) {
            val ajaxBody = FormBody.Builder()
                .add("query", q).apply { if (sessionUserHash.isNotEmpty()) add("user_hash", sessionUserHash) }
                .build()
            try {
                htmlHttpClient.postHtml(absoluteUrl("engine/ajax/search.php"), ajaxBody, isAjax = true)?.let {
                    allResults.addAll(parser.parseSearch(it))
                }
            } catch (e: Exception) {
                AppLogger.w("$name:Search", "AJAX search failed: ${e.message}")
            }
        }

        allResults.filter { it.title.isNotEmpty() && !it.pageUrl.contains("/?do=") && !it.pageUrl.endsWith("/") }
            .distinctBy { it.pageUrl }.take(limit)
            .map { SearchItem(it.title, it.pageUrl, it.poster, name) }
    }

    override suspend fun getMovieDetails(url: String): MovieDetail = withContext(Dispatchers.IO) {
        detailCache.get(url)?.let { return@withContext it }
        PerformanceMonitor.begin("UakinoProvider.getMovieDetails")
        try {
            htmlHttpClient.getHtml(url)?.let { html ->
                pageHtmlCache.put(url, html)
                parser.parseDetail(html, url).also { detailCache.put(url, it) }
            } ?: throw Exception("Empty response")
        } catch (e: Exception) {
            throw Exception("Failed to load details for $url: ${e.message}")
        } finally {
            PerformanceMonitor.end()
        }
    }

    override suspend fun getMediaSource(pageUrl: String, season: Int?, episode: Int?, isDeep: Boolean, prefetchedHtml: String?): MediaSource? = withContext(Dispatchers.IO) {
        if (sessionUserHash.isEmpty()) initializeSession()
        val html = prefetchedHtml ?: pageHtmlCache.get(pageUrl).also { pageHtmlCache.invalidate(pageUrl) } ?: try { htmlHttpClient.getHtml(pageUrl, baseUrl) ?: "" } catch (_: Exception) { "" }

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
            val finalUrl = resolveBestUrlFromPlaylist(best) ?: best
            return MediaSource.Movie(finalUrl, directUrls.filter { it != best }, pageUrl, name)
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
                    val finalUrl = resolveBestUrlFromPlaylist(best) ?: best
                    val fallbacks = media.filter { it != best }
                    return MediaSource.Movie(finalUrl, fallbacks, pageUrl, name)
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

    private suspend fun resolveBestUrlFromPlaylist(mediaUrl: String, referer: String = baseUrl): String? {
        if (!mediaUrl.contains(".m3u8")) return null
        try {
            val playlist = htmlHttpClient.getHtml(mediaUrl, referer) ?: return null
            val variants = DleResolutionUtils.parseMasterPlaylist(playlist)
            if (variants.isEmpty()) return null
            val best = variants.maxBy { it.height }
            AppLogger.d(name, "Master playlist parsed, picked ${best.height}p from ${variants.size} variants")
            return best.url
        } catch (e: Exception) {
            AppLogger.w(name, "Failed to parse master playlist: ${e.message}")
            return null
        }
    }

    private suspend fun selectBestMediaUrl(media: List<String>): String? {
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

    private fun resolveOtherSeasons(doc: org.jsoup.nodes.Document, pageUrl: String): List<Pair<Int, String>> =
        DleResolutionUtils.resolveOtherSeasons(doc, pageUrl, "$name:OtherSeasons")

    override fun clearCache(url: String?) { detailCache.clear(); pageHtmlCache.clear() }

    private fun absoluteUrl(href: String): String =
        if (href.startsWith("http")) href else baseUrl.trimEnd('/') + "/" + href.trimStart('/')
}
