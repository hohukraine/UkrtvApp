package ua.ukrtv.app.data.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.repository.CatalogRepository
import ua.ukrtv.app.data.repository.SessionRepository

import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor

class EneyidaProvider(
    private val htmlHttpClient: HtmlHttpClient,
    private val sessionRepository: SessionRepository,
    private val catalogRepository: CatalogRepository
) : MediaProvider {

    private val parser = DleParser(EneyidaProfile)
    private val pageHtmlCache = TtlLruCache<String, String>(20, 30 * 60 * 1000L)
    private var sessionUserHash: String = ""

    companion object {
        private val SESSION_HASH_REGEX = Regex("""dle_login_hash\s*=\s*['"]([^'"]+)['"]""")
        private val FILE_JSON_REGEX = Regex("""file\s*:\s*['"](\[.{0,50000})['"]""", RegexOption.DOT_MATCHES_ALL)
    }

    override val name: String = "Eneyida"
    override val baseUrl: String = "https://eneyida.tv/"
    override val brandColor: String = "#31C469"

    override fun getHomeCategories(): List<ContentCategory> = EneyidaProfile.categoryPaths.keys.toList()
    override fun supportsUrl(url: String) = url.contains("eneyida.tv")

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
            val movies = FastHomeParser.parseListOptimized(html, baseUrl, EneyidaProfile.selectors)
            if (movies.isNotEmpty()) listOf(HomeSection("Новинки", movies)) else emptyList()
        } catch (e: Exception) {
            AppLogger.w("$name:HomeSections", "Failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getMoviesByCategory(category: ContentCategory, page: Int): List<Movie> = withContext(Dispatchers.IO) {
        val path = EneyidaProfile.categoryPaths[category] ?: return@withContext emptyList()
        val fullUrl = absoluteUrl(if (page > 1) "${path}page/$page/" else path)
        AppLogger.d(name, "Fetching category $category from $fullUrl")
        try {
            htmlHttpClient.getHtml(fullUrl)?.let { html ->
                val parsed = FastHomeParser.parseListOptimized(html, baseUrl, EneyidaProfile.selectors)
                AppLogger.d(name, "Parsed ${parsed.size} items for $category")
                if (parsed.isEmpty() && category == ContentCategory.TRENDS && page == 1) {
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
        val q = query.trim().takeIf { it.length >= 3 || (it.length >= 2 && it.any { c -> c.isDigit() }) } ?: return@withContext emptyList()

        if (catalogRepository.isProviderReady(name)) {
            val results = catalogRepository.searchByProvider(name, q, limit)
            if (results.isNotEmpty()) {
                return@withContext results.map { SearchItem(it.title, it.url, it.poster, name) }
            }
        }

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
        PerformanceMonitor.begin("EneyidaProvider.getMovieDetails")
        try {
            htmlHttpClient.getHtml(url, baseUrl)?.let { html ->
                pageHtmlCache.put(url, html)
                parser.parseDetail(html, url)
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

        // 1. Try to resolve as a playlist (Series/Multivolume)
        var source = resolveSeriesPage(html, pageUrl, season, episode, isDeep)
        
        // 2. If no playlist found, try direct media extraction (Movie)
        if (source == null) {
            source = resolveMoviePage(html, pageUrl)
        }

        return@withContext DleResolutionUtils.promoteToSeriesIfNeeded(source, pageUrl, name)
    }

    private suspend fun resolveMoviePage(html: String, pageUrl: String, doc: Document? = null): MediaSource? {
        val directUrls = DleResolutionUtils.findMediaUrlsInText(html)
        if (directUrls.isNotEmpty()) {
            return MediaSource.Movie(directUrls.first(), directUrls.drop(1), pageUrl, name)
        }

        val parsedDoc = doc ?: Jsoup.parse(html, pageUrl)
        val allIframes = parsedDoc.select("iframe").map { it.attr("abs:data-src").ifEmpty { it.attr("abs:src") } }
        val iframes = allIframes.filter { src ->
            src.isNotEmpty() && !src.contains("youtube") && !src.contains("facebook")
        }
        AppLogger.d(name, "resolveMoviePage: filtered iframes=${iframes.size} srcs=${iframes.take(5)}")

        for (src in iframes) {
            try {
                var iframeResp = htmlHttpClient.getHtml(src, pageUrl)
                if (iframeResp == null || iframeResp.isEmpty()) {
                    iframeResp = htmlHttpClient.getHtml(src, pageUrl, isAjax = true)
                }
                if (iframeResp == null || iframeResp.isEmpty()) {
                    continue
                }

                val media = DleResolutionUtils.findMediaUrlsInText(iframeResp)
                if (media.isNotEmpty()) {
                    if (media.size > 2) {
                        val series = SeriesPlaylistParser.parseUrlBasedSeries(media, pageUrl, name)
                        if (series != null) return series
                    }

                    val best = selectBestMediaUrl(media) ?: media.first()
                    val fallbacks = media.filter { it != best }
                    return MediaSource.Movie(best, fallbacks, pageUrl, name)
                }
            } catch (e: Exception) {
                AppLogger.w(name, "resolveMoviePage: exception fetching $src: ${e.message}")
            }
        }

        return null
    }

    private suspend fun selectBestMediaUrl(media: List<String>): String? {
        if (media.isEmpty()) return null
        if (media.size > 1) return DleResolutionUtils.pickBestQuality(media) ?: media.first()
        return media.first()
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

    private suspend fun resolveSeriesPage(
        html: String, pageUrl: String,
        season: Int?, episode: Int?, isDeep: Boolean
    ): MediaSource? {
        val doc = Jsoup.parse(html, pageUrl)
        val iframes = doc.select("iframe").mapNotNull { ifr ->
            val src = ifr.attr("abs:data-src").ifEmpty { ifr.attr("abs:src") }
            if (src.isEmpty() || src.contains("youtube") || src.contains("facebook")) null
            else src
        }

        if (iframes.isEmpty()) return null

        for (src in iframes) {
            try {
                val iframeResp = htmlHttpClient.getHtml(src, pageUrl) ?: continue

                val jsonPlaylist = try {
                    val fileMatch = FILE_JSON_REGEX.find(iframeResp)?.groupValues?.get(1)
                    fileMatch?.let { SeriesPlaylistParser.extractBalancedJson(it) }
                } catch (e: Exception) {
                    AppLogger.w("$name:JsonPlaylist", "Extraction failed: ${e.message}")
                    null
                }

                if (jsonPlaylist != null) {
                    try {
                        val parsed = SeriesPlaylistParser.parseJsonPlaylist(jsonPlaylist, pageUrl, name)
                        if (parsed != null) return parsed
                    } catch (e: Exception) {
                        AppLogger.w(name, "JSON playlist parse failed: ${e.message}")
                    }
                }

                val media = DleResolutionUtils.findMediaUrlsInText(iframeResp)
                if (media.isNotEmpty()) {
                    val series = SeriesPlaylistParser.parseUrlBasedSeries(media, pageUrl, name)
                    if (series != null) return series
                }
            } catch (e: Exception) {
                AppLogger.w("$name:SeriesIframe", "Iframe resolution failed: ${e.message}")
            }
        }

        if (isDeep) {
            val otherSeasons = resolveOtherSeasons(doc, pageUrl)
            if (otherSeasons.isNotEmpty()) {
                return deepResolveSeasons(otherSeasons, html, pageUrl)
            }
        }

        return null
    }

    private suspend fun deepResolveSeasons(
        otherSeasons: List<Pair<Int, String>>,
        html: String, pageUrl: String
    ): MediaSource? {
        val allSeasons = mutableListOf<ProviderSeason>()
        val semaphore = Semaphore(1)

        val results: List<List<ProviderSeason>?> = coroutineScope {
            otherSeasons.take(12).map { (num, sUrl) ->
                async(Dispatchers.IO) {
                    val result: List<ProviderSeason>? = semaphore.withPermit {
                        try {
                            AppLogger.d(name, "Deep fetching S$num: $sUrl")
                            val sHtml = htmlHttpClient.getHtml(sUrl) ?: return@withPermit null
                            val media = DleResolutionUtils.findMediaUrlsInText(sHtml)
                            if (media.isNotEmpty()) {
                                val sDoc = Jsoup.parse(sHtml)
                                val iframes = sDoc.select("iframe").mapNotNull { ifr ->
                                    val src = ifr.attr("abs:data-src").ifEmpty { ifr.attr("abs:src") }
            if (src.isEmpty() || src.contains("youtube") || src.contains("facebook")) null
            else src
                                }
                                for (src in iframes) {
                                    try {
                                        val iframeResp = htmlHttpClient.getHtml(src, sUrl) ?: continue
                                        val jsonPlaylist = try {
                                            val fileMatch = FILE_JSON_REGEX.find(iframeResp)?.groupValues?.get(1)
                                            fileMatch?.let { SeriesPlaylistParser.extractBalancedJson(it) }
                                        } catch (e: Exception) {
                                            AppLogger.w("$name:DeepJson", "JSON extraction failed: ${e.message}")
                                            null
                                        }
                                        if (jsonPlaylist != null) {
                                            val parsed = SeriesPlaylistParser.parseJsonPlaylist(jsonPlaylist, sUrl, name)
                                            if (parsed != null) return@withPermit parsed.seasons
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.w("Eneyida:DeepIframe", "Iframe failed: ${e.message}")
                                    }
                                }
                                return@withPermit listOf(ProviderSeason(num, listOf(ProviderEpisode(1, "Серія", media.first()))))
                            }
                            null
                        } catch (e: Exception) {
                            AppLogger.w("$name:DeepResolve", "Failed S$num: ${e.message}")
                            null
                        }
                    }
                    result
                }
            }.awaitAll()
        }

        results.forEach { seasonList ->
            seasonList?.forEach { s ->
                if (allSeasons.none { it.number == s.number }) allSeasons.add(s)
            }
        }

        if (allSeasons.isEmpty()) return null
        val merged = allSeasons.groupBy { it.number }
            .map { (num, list) ->
                val allEps = list.flatMap { it.episodes }.distinctBy { it.url }.sortedBy { it.number }
                val allVos = list.flatMap { it.voiceoverOptions }.distinct().sorted()
                ProviderSeason(num, allEps, voiceoverOptions = allVos)
            }.sortedBy { it.number }
        return MediaSource.Series(merged, pageUrl, name)
    }

    private fun resolveOtherSeasons(doc: org.jsoup.nodes.Document, pageUrl: String): List<Pair<Int, String>> =
        DleResolutionUtils.resolveOtherSeasons(doc, pageUrl, "$name:OtherSeasons")

    override fun clearCache(url: String?) { pageHtmlCache.clear() }

    private fun absoluteUrl(href: String): String =
        if (href.startsWith("http")) href else baseUrl.trimEnd('/') + "/" + href.trimStart('/')
}
