package ua.ukrtv.app.data.providers

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.parsing.UakinoParser
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UakinoProvider @Inject constructor(
    client: OkHttpClient,
    htmlHttpClient: HtmlHttpClient,
    private val parser: UakinoParser,
    private val uakinoSeasonParser: ua.ukrtv.app.data.parsing.UakinoSeasonParser,
    private val uakinoApi: ua.ukrtv.app.data.api.UakinoApi,
    private val unifiedStreamProvider: ua.ukrtv.app.data.streaming.UnifiedStreamProvider
) : BaseStreamProvider(client, htmlHttpClient), ContentProvider {

    override val name = "Uakino"
    override val baseUrl = "https://uakino.best/"
    override val hasPublicSearch = true
    override val brandColor = "#E50914"
    override val logoUrl = "${baseUrl}templates/uakino/images/logo.png"

    override fun supportsUrl(url: String) =
        url.contains("uakino.best") || url.contains("uakino.net") ||
        url.contains("uakino.me") || url.contains("uakino.org")

    override suspend fun initializeSession(): Boolean {
        val result = getHtml("${baseUrl}ua/")
        AppLogger.d(name, "Initialize session: ${result != null}")
        return result != null
    }

    override suspend fun getHomeSections(page: Int): List<HomeSection> = withContext(Dispatchers.IO) {
        val path = if (page > 1) "ua/page/$page/" else "ua/"
        AppLogger.d(name, "Fetching home sections: $path")
        
        try {
            val html = uakinoApi.getPage(path)
            val doc = Jsoup.parse(html, baseUrl + path)
            val movies = parser.parseMovies(doc).map { it.toMovie() }
            
            if (movies.isNotEmpty()) {
                listOf(HomeSection("Новинки", movies))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.e(name, "Error fetching home sections", e)
            emptyList()
        }
    }

    override suspend fun getPopular(category: ContentCategory, page: Int): List<Movie> = withContext(Dispatchers.IO) {
        val categoryPath = when (category) {
            ContentCategory.MOVIES -> "films/"
            ContentCategory.SERIES -> "seriesss/"
            ContentCategory.ANIME -> "animeukr/"
            ContentCategory.CARTOONS -> "cartoons/"
        }
        val path = if (page > 1) "${categoryPath}page/$page/" else categoryPath
        
        try {
            val html = uakinoApi.getPage(path)
            parser.parseMovies(Jsoup.parse(html, baseUrl + path)).map { it.toMovie() }
        } catch (e: Exception) {
            AppLogger.e(name, "Error fetching popular category=$category", e)
            emptyList()
        }
    }

    override suspend fun search(query: String, limit: Int): List<SearchItem> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext emptyList()

        try {
            val ajaxHtml = uakinoApi.search(trimmedQuery)
            if (ajaxHtml.isNotBlank()) {
                val doc = Jsoup.parse(ajaxHtml, baseUrl)
                val movies = parser.parseMovies(doc)
                if (movies.isNotEmpty()) {
                    return@withContext movies.take(limit).map { it.toSearchItem() }
                }
            }
        } catch (e: Exception) {
            AppLogger.d(name, "AJAX search failed: ${e.message}")
        }

        // Fallback
        try {
            val encodedQuery = java.net.URLEncoder.encode(trimmedQuery, "UTF-8")
            val path = "index.php?do=search&subaction=search&story=$encodedQuery"
            val html = uakinoApi.getPage(path)
            parser.parseMovies(Jsoup.parse(html, baseUrl + path)).take(limit).map { it.toSearchItem() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getDetails(id: String, url: String): MovieDetail = withContext(Dispatchers.IO) {
        val html = getHtml(url) ?: throw Exception("Failed to load details")
        val doc = Jsoup.parse(html)
        // Only basic details without heavy playlist parsing
        parser.parseMovieDetails(doc, url)
    }


    override suspend fun getMediaSource(pageUrl: String): MediaSource? = withContext(Dispatchers.IO) {
        // 1. Try to extract direct links, if URL is already an iframe
        if (pageUrl.contains("/vod/") || pageUrl.contains("ashdi.vip") || pageUrl.contains("hdvb")) {
            val videoID = pageUrl.substringAfterLast("/")
            val stream = unifiedStreamProvider.getStreamUrl(videoID)
            if (stream != null) return@withContext MediaSource.Movie(stream, emptyList(), pageUrl, name)
        }

        // 2. Get HTML page for extracting technical parameters
        val html = try {
            val path = if (pageUrl.startsWith(baseUrl)) pageUrl.removePrefix(baseUrl) else pageUrl
            uakinoApi.getPage(path)
        } catch (e: Exception) {
            return@withContext null
        }

        // 3. Try fallback to full season parsing FIRST for series to ensure DetailScreen sees episodes
        val isSeries = pageUrl.contains("/seriesss/") || pageUrl.contains("/seriali/") || 
                      pageUrl.contains("/anime/") || pageUrl.contains("/animeukr/") || 
                      pageUrl.contains("/cartoons/") || pageUrl.contains("/cartoon/") ||
                      html.contains("сезон") || html.contains("серія")

        if (isSeries) {
            val parsed = uakinoSeasonParser.parseSeries(pageUrl = pageUrl, html = html, referer = pageUrl)
            if (parsed is MediaSource.Series && parsed.seasons.any { it.episodes.isNotEmpty() }) {
                return@withContext parsed
            }
        }

        // 4. Try UnifiedStreamProvider with newsId
        val newsId = Regex("""data-news_id=["'](\d+)["']""").find(html)?.groupValues?.get(1)
            ?: pageUrl.substringAfterLast("/").substringBefore("-").takeIf { it.all { c -> c.isDigit() } }
            ?: ""
            
        if (newsId.isNotEmpty()) {
            val stream = unifiedStreamProvider.getStreamUrl(newsId)
            if (stream != null) return@withContext MediaSource.Movie(stream, emptyList(), pageUrl, name)
            
            // Fallback to AJAX playlist if unified failed
            val xfname = if (isSeries) "seria" else "playlist"
            val edittime = Regex("""dle_edittime\s*=\s*['"](\d+)['"]""").find(html)?.groupValues?.get(1) 
                ?: (System.currentTimeMillis() / 1000).toString()
                
            val direct = parser.getStreamByNewsId(newsId, xfname, edittime, pageUrl)
            if (direct != null) return@withContext direct
        }

        val doc = Jsoup.parse(html, pageUrl)
        parser.getStreamUrl(doc, pageUrl)
    }
}