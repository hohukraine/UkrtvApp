package ua.ukrtv.app.data.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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
            val movies = FastHomeParser.parseListOptimized(html, baseUrl, profile.selectors)
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
                val parsed = FastHomeParser.parseListOptimized(html, baseUrl, profile.selectors)
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

    protected fun resolveOtherSeasons(doc: org.jsoup.nodes.Document, pageUrl: String): List<Pair<Int, String>> =
        DleResolutionUtils.resolveOtherSeasons(doc, pageUrl, "$name:OtherSeasons")

    override fun clearCache(url: String?) { pageHtmlCache.clear() }

    protected fun absoluteUrl(href: String): String =
        if (href.startsWith("http")) href else baseUrl.trimEnd('/') + "/" + href.trimStart('/')
}
