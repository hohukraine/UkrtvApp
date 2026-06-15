package ua.ukrtv.app.data.providers

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.parsing.EneyidaParser
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EneyidaProvider @Inject constructor(
    client: OkHttpClient,
    htmlHttpClient: HtmlHttpClient,
    private val parser: EneyidaParser,
    private val eneyidaSeasonParser: ua.ukrtv.app.data.parsing.EneyidaSeasonParser,
    private val eneyidaApi: ua.ukrtv.app.data.api.EneyidaApi,
    private val unifiedStreamProvider: ua.ukrtv.app.data.streaming.UnifiedStreamProvider
) : BaseStreamProvider(client, htmlHttpClient), ContentProvider {

    override val name = "Eneyida"
    override val baseUrl = "https://eneyida.tv/"
    override val hasPublicSearch = true
    override val brandColor = "#FFD700"
    override val logoUrl = "${baseUrl}templates/eneyida/images/logo.png"

    override fun supportsUrl(url: String) =
        url.contains("eneyida.tv") || url.contains("eneyida.com") ||
        url.contains("eneyida.org")

    override suspend fun initializeSession(): Boolean {
        val result = getHtml(baseUrl)
        AppLogger.d(name, "Initialize session: ${result != null}")
        return result != null
    }

    override suspend fun getHomeSections(page: Int): List<HomeSection> = withContext(Dispatchers.IO) {
        val path = if (page > 1) "page/$page/" else ""
        AppLogger.d(name, "Fetching home sections: $path")
        
        try {
            val html = eneyidaApi.getPage(path)
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
            ContentCategory.SERIES -> "series/"
            ContentCategory.ANIME -> "anime/"
            ContentCategory.CARTOONS -> "cartoon/"
        }
        val path = if (page > 1) "${categoryPath}page/$page/" else categoryPath
        
        try {
            val html = eneyidaApi.getPage(path)
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
            val ajaxHtml = eneyidaApi.search(trimmedQuery)
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

        try {
            val encodedQuery = java.net.URLEncoder.encode(trimmedQuery, "UTF-8")
            val path = "index.php?do=search&subaction=search&story=$encodedQuery"
            val html = eneyidaApi.getPage(path)
            parser.parseMovies(Jsoup.parse(html, baseUrl + path)).take(limit).map { it.toSearchItem() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getDetails(id: String, url: String): MovieDetail = withContext(Dispatchers.IO) {
        val html = getHtml(url) ?: throw Exception("Failed to load details")
        val doc = Jsoup.parse(html)
        // Return details only from page parser, without heavy playlist parsing at this stage.
        // Seasons will be obtained by the player via getMediaSource() as needed.
        parser.parseMovieDetails(doc, url)
    }


    override suspend fun getMediaSource(pageUrl: String): MediaSource? = withContext(Dispatchers.IO) {
        // 1. Try UnifiedStreamProvider if URL is already an iframe
        if (pageUrl.contains("/vod/") || pageUrl.contains("ashdi.vip") || pageUrl.contains("hdvb")) {
            val videoID = pageUrl.substringAfterLast("/")
            val stream = unifiedStreamProvider.getStreamUrl(videoID)
            if (stream != null) return@withContext MediaSource.Movie(stream, emptyList(), pageUrl, name)
        }

        // 2. Get HTML page
        val html = try {
            val path = if (pageUrl.startsWith(baseUrl)) pageUrl.removePrefix(baseUrl) else pageUrl
            eneyidaApi.getPage(path)
        } catch (e: Exception) {
            return@withContext null
        }

        // 3. Extract newsId and try UnifiedStreamProvider
        val newsId = Regex("""data-news_id=["'](\d+)["']""").find(html)?.groupValues?.get(1)
            ?: pageUrl.substringAfterLast("/").substringBefore("-").takeIf { it.all { it.isDigit() } }
            ?: ""
            
        if (newsId.isNotEmpty()) {
            val stream = unifiedStreamProvider.getStreamUrl(newsId)
            if (stream != null) return@withContext MediaSource.Movie(stream, emptyList(), pageUrl, name)

            // Fallback to AJAX
            val isSeries = pageUrl.contains("/series/") || pageUrl.contains("/serialy/") || 
                          pageUrl.contains("/seriali/") || pageUrl.contains("/anime/") || 
                          pageUrl.contains("/cartoon-series/") || pageUrl.contains("/multfilm-serials/")
            val xfname = if (isSeries) "seria" else "playlist"
            val edittime = Regex("""dle_edittime\s*=\s*['"](\d+)['"]""").find(html)?.groupValues?.get(1) 
                ?: (System.currentTimeMillis() / 1000).toString()
                
            val direct = parser.getStreamByNewsId(newsId, xfname, edittime, pageUrl)
            if (direct != null) return@withContext direct
        }
        
        // 4. Try specialized season parser
        val parsed = eneyidaSeasonParser.parseSeries(pageUrl = pageUrl, html = html, referer = pageUrl)
        if (parsed != null) return@withContext parsed

        val doc = Jsoup.parse(html, pageUrl)
        parser.getStreamUrl(doc, pageUrl)
    }
}