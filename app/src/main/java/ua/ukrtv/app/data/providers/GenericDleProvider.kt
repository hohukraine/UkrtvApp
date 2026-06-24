package ua.ukrtv.app.data.providers

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.providers.DleParser
import ua.ukrtv.app.data.providers.strategies.*
import ua.ukrtv.app.data.repository.SessionRepository
import ua.ukrtv.app.data.streaming.UnifiedStreamProvider
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.util.AppLogger

class GenericDleProvider(
    private val client: OkHttpClient,
    private val htmlHttpClient: HtmlHttpClient,
    private val unifiedStreamProvider: UnifiedStreamProvider,
    private val sessionRepository: SessionRepository,
    private val profile: DleProviderProfile
) : MediaProvider {

    private val config = DleConfig(
        profile = profile,
        parsingStrategy = JsoupParsingStrategy(DleParser(profile)),
        resolutionChain = ResolutionChain(
            listOf(AjaxPlaylistResolutionStrategy(), IframeResolutionStrategy())
        )
    )

    override val id: String = profile.name.lowercase()
    override val name: String = profile.name
    override val baseUrl: String = profile.baseUrl
    override val brandColor: String = profile.brandColor
    override val logoUrl: String = profile.logoUrl

    private val detailCache = TtlLruCache<String, MovieDetail>(50, Constants.METADATA_CACHE_TTL_MS)
    private val sessionMutex = Mutex()
    private var sessionUserHash: String = ""

    override fun getHomeCategories(): List<ContentCategory> = config.profile.categoryPaths.keys.toList()
    override fun supportsUrl(url: String) = url.contains(baseUrl.substringAfter("://").trimEnd('/'))

    override suspend fun initializeSession(): Boolean = sessionMutex.withLock {
        if (sessionUserHash.isNotEmpty()) return true

        sessionRepository.getSessionHash(name)?.let {
            sessionUserHash = it
            return true
        }

        withContext(Dispatchers.IO) {
            try {
                val html = htmlHttpClient.getHtml(baseUrl) ?: ""
                sessionUserHash = Regex("""dle_login_hash\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1) ?: ""

                if (sessionUserHash.isEmpty()) {
                    val searchHtml = htmlHttpClient.getHtml(absoluteUrl("index.php?do=search")) ?: ""
                    sessionUserHash = Regex("""dle_login_hash\s*=\s*['"]([^'"]+)['"]""").find(searchHtml)?.groupValues?.get(1) ?: ""
                }

                if (sessionUserHash.isNotEmpty()) {
                    sessionRepository.saveSessionHash(name, sessionUserHash)
                }
                sessionUserHash.isNotEmpty()
            } catch (_: Exception) { false }
        }
    }

    override suspend fun getHomeSections(page: Int): List<HomeSection> = withContext(Dispatchers.IO) {
        val path = if (page > 1) "page/$page/" else ""
        try {
            htmlHttpClient.getHtml(absoluteUrl(path))?.let { html ->
                val doc = Jsoup.parse(html, baseUrl)
                val movies = config.parsingStrategy.extractMovies(doc)
                if (movies.isNotEmpty()) listOf(HomeSection("Новинки", movies)) else emptyList()
            } ?: emptyList()
        } catch (e: Exception) {
            AppLogger.w("$name:HomeSections", "Failed to load: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getMoviesByCategory(category: ContentCategory, page: Int): List<Movie> = withContext(Dispatchers.IO) {
        config.profile.categoryPaths[category]?.let { path ->
            val fullUrl = absoluteUrl(if (page > 1) "${path}page/$page/" else path)
            try {
                htmlHttpClient.getHtml(fullUrl)?.let { html ->
                    config.parsingStrategy.extractMovies(html)
                }
            } catch (e: Exception) {
                AppLogger.w("$name:Category", "Failed: ${e.message}")
                emptyList()
            }
        } ?: emptyList()
    }

    override suspend fun search(query: String, limit: Int): List<SearchItem> = withContext(Dispatchers.IO) {
        val q = query.trim().takeIf { it.length >= 3 } ?: return@withContext emptyList()
        if (sessionUserHash.isEmpty()) initializeSession()

        val allResults = mutableListOf<Movie>()

        val body = okhttp3.FormBody.Builder()
            .add("do", "search").add("subaction", "search").add("story", q)
            .apply { if (sessionUserHash.isNotEmpty()) add("user_hash", sessionUserHash) }
            .build()

        try {
            htmlHttpClient.postHtml(absoluteUrl("index.php?do=search"), body, baseUrl)?.let {
                allResults.addAll(config.parsingStrategy.extractSearch(it))
            }
        } catch (_: Exception) {}

        if (allResults.isEmpty()) {
            val ajaxBody = okhttp3.FormBody.Builder()
                .add("query", q).apply { if (sessionUserHash.isNotEmpty()) add("user_hash", sessionUserHash) }
                .build()
            try {
                htmlHttpClient.postHtml(absoluteUrl("engine/ajax/search.php"), ajaxBody, isAjax = true)?.let {
                    allResults.addAll(config.parsingStrategy.extractSearch(it))
                }
            } catch (_: Exception) {}
        }

        allResults.filter { it.title.isNotEmpty() && !it.pageUrl.contains("/?do=") && !it.pageUrl.endsWith("/") }
            .distinctBy { it.pageUrl }.take(limit)
            .map { SearchItem(it.title, it.pageUrl, it.poster, name, it.type, it.year) }
    }

    override suspend fun getMovieDetails(url: String): MovieDetail = withContext(Dispatchers.IO) {
        detailCache.get(url)?.let { return@withContext it }
        try {
            htmlHttpClient.getHtml(url)?.let { html ->
                config.parsingStrategy.extractDetail(html, url).also { detailCache.put(url, it) }
            } ?: throw Exception("Empty response")
        } catch (e: Exception) {
            throw Exception("Failed to load details for $url: ${e.message}")
        }
    }

    override suspend fun getMediaSource(pageUrl: String, season: Int?, episode: Int?, isDeep: Boolean): MediaSource? = withContext(Dispatchers.IO) {
        if (sessionUserHash.isEmpty()) initializeSession()
        val html = try { htmlHttpClient.getHtml(pageUrl, baseUrl) ?: "" } catch (_: Exception) { "" }

        val hasSeasonMarkers = html.contains("сезон", true) || html.contains("season", true) ||
                pageUrl.contains("-sezon", true) || pageUrl.contains("/series/", true) ||
                pageUrl.contains("/serialy/", true) || pageUrl.contains("/anime/", true)
        val detectedType = if (
            (hasSeasonMarkers && html.contains("<li") && html.contains("data-file")) ||
            config.profile.seriesMarkers.any { pageUrl.contains(it) }
        ) ContentType.SERIES else ContentType.MOVIE

        val ctx = ResolutionContext(pageUrl, html, sessionUserHash, detectedType, htmlHttpClient, unifiedStreamProvider, config, isDeep = isDeep)
        AppLogger.d("$name:MediaSource", "Resolving: isDeep=$isDeep, detectedType=$detectedType, season=$season")
        config.resolutionChain.resolve(ctx)
    }

    override fun clearCache(url: String?) { detailCache.clear() }
    private fun absoluteUrl(href: String): String = if (href.startsWith("http")) href else baseUrl.trimEnd('/') + "/" + href.trimStart('/')
}
