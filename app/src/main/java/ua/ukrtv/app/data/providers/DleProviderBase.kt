package ua.ukrtv.app.data.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import org.jsoup.Jsoup
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.repository.CatalogRepository
import ua.ukrtv.app.data.repository.SessionRepository
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor

abstract class DleProviderBase(
    protected val htmlHttpClient: HtmlHttpClient,
    protected val sessionRepository: SessionRepository,
    protected val catalogRepository: CatalogRepository,
    protected val profile: DleProviderProfile
) : MediaProvider {

    protected val parser = DleParser(profile)
    protected val pageHtmlCache = TtlLruCache<String, String>(20, 30 * 60 * 1000L)
    protected var sessionUserHash: String = ""

    companion object {
        protected val SESSION_HASH_REGEX = Regex("""dle_login_hash\s*=\s*['"]([^'"]+)['"]""")
    }

    override fun getHomeCategories(): List<ContentCategory> = profile.categoryPaths.keys.toList()

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
            val movies = parser.parseList(html).distinctBy { it.pageUrl }.take(30)
            if (movies.isNotEmpty()) listOf(HomeSection("Новинки", movies)) else emptyList()
        } catch (e: Exception) {
            AppLogger.w("$name:HomeSections", "Failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getMoviesByCategory(category: ContentCategory, page: Int): List<Movie> = withContext(Dispatchers.IO) {
        val path = profile.categoryPaths[category] ?: return@withContext emptyList()
        val fullUrl = absoluteUrl(if (page > 1) "${path}page/$page/" else path)
        try {
            htmlHttpClient.getHtml(fullUrl)?.let { html ->
                val parsed = parser.parseList(html)
                if (parsed.isEmpty() && category == ContentCategory.TRENDS && page == 1) {
                    htmlHttpClient.getHtml(baseUrl)?.let { mainHtml ->
                        parser.parseList(mainHtml)
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

    protected suspend fun performDleSearch(query: String, limit: Int): List<Movie> {
        val q = query.trim().takeIf { it.length >= 3 || (it.length >= 2 && it.any { c -> c.isDigit() }) } ?: return emptyList()

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

        return allResults.filter { it.title.isNotEmpty() && !it.pageUrl.contains("/?do=") && !it.pageUrl.endsWith("/") }
            .distinctBy { it.pageUrl }.take(limit)
    }

    override suspend fun getMovieDetails(url: String): MovieDetail = withContext(Dispatchers.IO) {
        PerformanceMonitor.begin("${this@DleProviderBase.javaClass.simpleName}.getMovieDetails")
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

    override suspend fun search(query: String, limit: Int): List<SearchItem> = withContext(Dispatchers.IO) {
        val q = query.trim().takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
        if (catalogRepository.isProviderReady(name)) {
            val results = catalogRepository.searchByProvider(name, q, limit)
            if (results.isNotEmpty()) {
                return@withContext results.map { SearchItem(it.title, it.url, it.poster, name) }
            }
        }
        val allResults = performDleSearch(q, limit)
        val mapped = allResults.map { SearchItem(it.title, it.pageUrl, it.poster, name) }
        if (mapped.isNotEmpty()) return@withContext mapped
        return@withContext searchFallback(q, limit)
    }

    protected open suspend fun searchFallback(query: String, limit: Int): List<SearchItem> = emptyList()

    override suspend fun getMediaSource(
        pageUrl: String, season: Int?, episode: Int?, isDeep: Boolean, prefetchedHtml: String?
    ): MediaSource? = withContext(Dispatchers.IO) {
        if (sessionUserHash.isEmpty()) initializeSession()
        val html = prefetchedHtml ?: pageHtmlCache.get(pageUrl).also { pageHtmlCache.invalidate(pageUrl) } ?: run {
            htmlHttpClient.getHtml(pageUrl, baseUrl)
                ?: throw java.io.IOException("Не вдалося завантажити сторінку: $pageUrl")
        }
        val doc = Jsoup.parse(html, pageUrl)
        var source = resolveSeriesContent(html, pageUrl, doc, season, episode, isDeep)
        if (source == null) {
            source = resolveMovieFromPage(html, pageUrl, doc)
        }
        return@withContext DleResolutionUtils.promoteToSeriesIfNeeded(source, pageUrl, name)
    }

    protected abstract suspend fun resolveSeriesContent(
        html: String, pageUrl: String, doc: org.jsoup.nodes.Document,
        season: Int?, episode: Int?, isDeep: Boolean
    ): MediaSource?

    protected fun resolveOtherSeasons(doc: org.jsoup.nodes.Document, pageUrl: String): List<Pair<Int, String>> =
        DleResolutionUtils.resolveOtherSeasons(doc, pageUrl, "$name:OtherSeasons")

    override fun clearCache(url: String?) {
        if (url != null) pageHtmlCache.invalidate(url) else pageHtmlCache.clear()
    }

    protected fun selectBestMediaUrl(media: List<String>): String? {
        if (media.isEmpty()) return null
        if (media.size > 1) return DleResolutionUtils.pickBestQuality(media) ?: media.first()
        return media.first()
    }

    protected fun extractIframes(doc: org.jsoup.nodes.Document): List<String> {
        return doc.select("iframe").mapNotNull { ifr ->
            val src = ifr.attr("abs:data-src").ifEmpty { ifr.attr("abs:src") }
            if (src.isEmpty() || src.contains("youtube") || src.contains("facebook")) null
            else src
        }
    }

    protected suspend fun resolveMovieFromPage(html: String, pageUrl: String, doc: org.jsoup.nodes.Document? = null): MediaSource? {
        val directUrls = DleResolutionUtils.findMediaUrlsInText(html)
        if (directUrls.isNotEmpty()) {
            val best = selectBestMediaUrl(directUrls) ?: directUrls.first()
            return MediaSource.Movie(best, directUrls.filter { it != best }, pageUrl, name)
        }

        val parsedDoc = doc ?: org.jsoup.Jsoup.parse(html, pageUrl)
        val iframes = extractIframes(parsedDoc)

        for (src in iframes.take(3)) {
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
        }
        return null
    }

    protected fun mergeSeasons(seasons: List<ProviderSeason>, pageUrl: String): MediaSource.Series? {
        if (seasons.isEmpty()) return null
        val merged = seasons.groupBy { it.number }
            .map { (num, list) ->
                val allEps = list.flatMap { it.episodes }.distinctBy { it.url }.sortedBy { it.number }
                val allVos = list.flatMap { it.voiceoverOptions }.distinct().sorted()
                ProviderSeason(num, allEps, voiceoverOptions = allVos)
            }.sortedBy { it.number }
        return MediaSource.Series(merged, pageUrl, name)
    }

    protected fun absoluteUrl(href: String): String =
        if (href.startsWith("http")) href else baseUrl.trimEnd('/') + "/" + href.trimStart('/')
}
