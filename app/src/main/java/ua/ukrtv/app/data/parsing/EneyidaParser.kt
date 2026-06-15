package ua.ukrtv.app.data.parsing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.ukrtv.app.data.model.ParsedMovie
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.providers.MediaSource
import ua.ukrtv.app.data.streaming.HlsExtractor
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EneyidaParser @Inject constructor(
    private val eneyidaApi: ua.ukrtv.app.data.api.EneyidaApi,
    private val unifiedStreamProvider: ua.ukrtv.app.data.streaming.UnifiedStreamProvider
) : ContentParser {

    private val baseUrl = "https://eneyida.tv/"

    override suspend fun parseMovies(doc: Document): List<ParsedMovie> {
        val movies = mutableListOf<ParsedMovie>()
        val container = doc.selectFirst("#dle-content, .items, .gridder, .grid-items, main") ?: doc
        val items = container.select(".short, .short-item, .shortstory, .movie-item, .th-item, .story, .grid-item, article")
        
        items.forEachIndexed { index, element ->
            try {
                currentCoroutineContext().ensureActive()
                if (element.parents().any { it.tagName() == "nav" || it.className().contains("menu", true) || 
                    it.className().contains("sidebar", true) || it.className().contains("header", true) ||
                    it.className().contains("footer", true) || it.className().contains("side", true) }) return@forEachIndexed

                val titleEl = element.selectFirst("h2 a, .short_title a, .short-title a, .movie-title a, .th-title a, a[href*='.html'], .title a")
                if (titleEl == null) {
                    if (index < 3) AppLogger.d("EneyidaParser", "Item $index: titleEl not found")
                    return@forEachIndexed
                }
                
                val rawTitle = titleEl.text().ifBlank { titleEl.ownText() }.ifBlank { titleEl.attr("title") }.trim()
                if (rawTitle.isEmpty() || rawTitle.length < 2) {
                    if (index < 3) AppLogger.d("EneyidaParser", "Item $index: rawTitle empty or too short: '$rawTitle'")
                    return@forEachIndexed
                }
                
                var pageUrl = titleEl.absUrl("href").ifEmpty { element.selectFirst("a[href*='.html']")?.absUrl("href") ?: "" }
                if (pageUrl.isEmpty()) {
                    val relUrl = titleEl.attr("href").ifEmpty { element.selectFirst("a")?.attr("href") ?: "" }
                    if (relUrl.isNotEmpty()) {
                        pageUrl = if (relUrl.startsWith("http")) relUrl else baseUrl.trimEnd('/') + (if (relUrl.startsWith("/")) "" else "/") + relUrl
                    }
                }
                
                if (pageUrl.isEmpty()) {
                    if (index < 3) AppLogger.d("EneyidaParser", "Item $index: pageUrl empty for $rawTitle")
                    return@forEachIndexed
                }
                
                val posterEl = element.selectFirst("img[src*='uploads'], .short_img img, .shortstory-img img, .movie-img img, img, .poster img")
                var poster = posterEl?.attr("abs:data-src")?.takeIf { it.isNotEmpty() }
                    ?: posterEl?.absUrl("src") ?: ""
                
                if (poster.isEmpty()) {
                    val relPoster = posterEl?.attr("data-src")?.takeIf { it.isNotEmpty() } ?: posterEl?.attr("src") ?: ""
                    if (relPoster.isNotEmpty()) {
                        poster = if (relPoster.startsWith("http")) relPoster else baseUrl.trimEnd('/') + (if (relPoster.startsWith("/")) "" else "/") + relPoster
                    }
                }
                
                if (poster.isEmpty()) {
                    if (index < 3) AppLogger.d("EneyidaParser", "Item $index: poster empty for $rawTitle")
                    return@forEachIndexed
                }

                val title = cleanTitle(rawTitle)
                val year = element.selectFirst(".short_subtitle, .shortstory-year, .movie-year, .year, .date")?.text()?.let { 
                    Regex("""\b(19|20)\d{2}\b""").find(it)?.value 
                }
                val genres = element.select(".short_subtitle a, .shortstory-genre a, .movie-genre a, .genres a").map { it.text().trim() }

                movies.add(
                    ParsedMovie(
                        id = pageUrl.substringAfterLast("/").substringBefore("-"),
                        title = title,
                        poster = poster,
                        posterAlt = title,
                        pageUrl = pageUrl,
                        type = inferContentType(pageUrl, rawTitle),
                        year = year,
                        genres = genres,
                        shortDescription = element.selectFirst(".short_text, .shortstory-text, .movie-desc, .story-text, .short-desc")?.text()?.trim()
                    )
                )
            } catch (e: Exception) {
                AppLogger.e("EneyidaParser", "Error parsing movie item $index", e)
            }
        }
        return movies.distinctBy { it.pageUrl }.filter { it.title.isNotEmpty() }
    }

    override suspend fun parseMovieDetails(doc: Document, url: String): MovieDetail {
        val rawTitle = doc.selectFirst("meta[property='og:title']")?.attr("content")
            ?: doc.selectFirst("h1, .full-title, .title-h1, .movie-title")?.text() ?: ""
        val title = cleanTitle(rawTitle)
        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")
            ?: doc.selectFirst(".fullstory-img img, .movie-img img, .full-img img")?.absUrl("src") ?: ""
        val description = doc.selectFirst("meta[property='og:description']")?.attr("content")
            ?: doc.selectFirst(".fullstory-text, #full-story, .movie-description, .video-desc, .movie-desc")?.text()?.trim() ?: ""
        
        val yearEl = doc.selectFirst(".fi-label:contains(Рік), .f-label:contains(Рік), .movie-info-item:contains(Рік)")
        val year = yearEl?.parent()?.selectFirst(".fi-desc, .deck-value, .f-desc, .info-desc, span")?.text()?.trim()
            ?: doc.selectFirst(".movie-year, .year")?.text()?.trim()
            
        val genreEl = doc.selectFirst(".fi-label:contains(Жанр), .f-label:contains(Жанр), .movie-info-item:contains(Жанр)")
        val genres = genreEl?.parent()?.select(".fi-desc a, .deck-value a, .f-desc a, .info-desc a, a")?.map { it.text().trim() }
            ?: doc.select(".movie-genres a, .fullstory-genres a, .genres a").map { it.text().trim() }
        
        return MovieDetail(
            id = url.substringAfterLast("/").substringBefore("-"),
            title = title,
            poster = poster,
            description = description,
            year = year,
            genres = genres,
            pageUrl = url,
            providerName = "Eneyida",
            seasons = null,
            streamUrl = null,
            contentType = inferContentType(url, rawTitle)
        )
    }

    override suspend fun getStreamUrl(doc: Document, url: String): MediaSource? = null

    suspend fun getStreamByNewsId(newsId: String, xfname: String, edittime: String, url: String): MediaSource? {
        val possibleXfields = listOf(xfname, if (xfname == "seria") "playlist" else "seria").distinct()
        
        for (xf in possibleXfields) {
            try {
                // Try both common AJAX endpoints
                val response = try {
                    eneyidaApi.getPlaylistRe(newsId, xf, edittime)
                } catch (e: Exception) {
                    eneyidaApi.getPlaylistGet(newsId, xf, edittime)
                }

                if (!response.success) continue
                
                val htmlToParse = response.response.replace("\\/", "/")
                if (htmlToParse.isBlank()) continue
                
                val d = Jsoup.parse(htmlToParse)
                if (d.select(".playlists-lists li").isNotEmpty()) return null
                
                val firstFile = d.selectFirst("li[data-file], li[data-src], li[data-url]")?.let { li ->
                    li.attr("data-file").ifEmpty { li.attr("data-src") }.ifEmpty { li.attr("data-url") }
                }
                
                if (!firstFile.isNullOrBlank()) {
                    val finalUrl = if (firstFile.startsWith("//")) "https:$firstFile" else firstFile
                    return MediaSource.Movie(finalUrl, emptyList(), url, "Eneyida")
                }
            } catch (e: Exception) {
                AppLogger.e("EneyidaParser", "AJAX request failed for xfield=$xf", e)
            }
        }
        return null
    }
}
