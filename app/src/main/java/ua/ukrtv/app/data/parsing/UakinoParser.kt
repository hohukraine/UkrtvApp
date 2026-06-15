package ua.ukrtv.app.data.parsing

import android.util.Log
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
class UakinoParser @Inject constructor(
    private val uakinoApi: ua.ukrtv.app.data.api.UakinoApi,
    private val unifiedStreamProvider: ua.ukrtv.app.data.streaming.UnifiedStreamProvider
) : ContentParser {

    private val tag = "UakinoParser"
    private val baseUrl = "https://uakino.best/"

    override suspend fun parseMovies(doc: Document): List<ParsedMovie> {
        val movies = mutableListOf<ParsedMovie>()
        val container = doc.selectFirst("#dle-content, .items, .movie-list, .movies-list, .grid-items, main") ?: doc
        // Extended selectors for different page layouts
        val items = container.select(".movie-item, .short-item, .th-item, .story, .shortstory, .short-story, .grid-item, .film-item, .item, article")
        
        AppLogger.d(tag, "Parsing movies: found ${items.size} items in container")
        
        items.forEachIndexed { index, element ->
            try {
                currentCoroutineContext().ensureActive()
                if (element.parents().any { it.tagName() == "nav" || it.className().contains("menu", true) || 
                    it.className().contains("sidebar", true) || it.className().contains("header", true) ||
                    it.className().contains("footer", true) }) return@forEachIndexed

                val titleEl = element.selectFirst(".movie-title, .th-title, .short-title, h2, h3, a[href*='.html'], .title a, .name a, .short-t, .title, .name, .movie__title, .card__title")
                if (titleEl == null) {
                    if (index < 3) AppLogger.d(tag, "Item $index: titleEl not found")
                    return@forEachIndexed
                }

                var rawTitle = titleEl.text().ifBlank { titleEl.ownText() }.ifBlank { titleEl.attr("title") }.trim()
                if (rawTitle.isEmpty() || rawTitle.length < 2) {
                    // Fallback to image alt
                    rawTitle = element.selectFirst("img")?.attr("alt")
                        ?.replace(" постер", "", ignoreCase = true)
                        ?.replace(" дивитися онлайн", "", ignoreCase = true)
                        ?.trim() ?: ""
                }

                if (rawTitle.isEmpty() || rawTitle.length < 2) {
                    if (index < 3) AppLogger.d(tag, "Item $index: rawTitle empty or too short: '$rawTitle'")
                    return@forEachIndexed
                }
                
                var pageUrl = titleEl.absUrl("href")
                    .ifEmpty { element.selectFirst("a[href*='.html']")?.absUrl("href") ?: "" }
                    .ifEmpty { element.selectFirst("a")?.absUrl("href") ?: "" }
                
                if (pageUrl.isEmpty()) {
                    val relUrl = titleEl.attr("href")
                        .ifEmpty { element.selectFirst("a[href*='.html']")?.attr("href") ?: "" }
                        .ifEmpty { element.selectFirst("a")?.attr("href") ?: "" }
                    if (relUrl.isNotEmpty()) {
                        pageUrl = if (relUrl.startsWith("http")) relUrl else baseUrl.trimEnd('/') + (if (relUrl.startsWith("/")) "" else "/") + relUrl
                    }
                }

                if (pageUrl.isEmpty()) {
                    if (index < 3) AppLogger.d(tag, "Item $index: pageUrl empty for $rawTitle")
                    return@forEachIndexed
                }
                
                val upperTitle = rawTitle.uppercase()
                if (upperTitle.contains("СВЯТКОВІ") || upperTitle.contains("НЕТФЛІКС") || 
                    upperTitle.contains("ПІДБІРКИ") || upperTitle.contains("КОЛЕКЦІЇ") ||
                    upperTitle.contains("ДОБІРКИ")) return@forEachIndexed

                val posterEl = element.selectFirst("img[src*='uploads'], .movie-img img, .th-img img, .short-img img, img, .poster img, .short-i img, .movie__img img, .card__img img")
                var poster = posterEl?.attr("abs:data-src")?.takeIf { it.isNotEmpty() }
                    ?: posterEl?.attr("abs:src")
                    ?: posterEl?.absUrl("src") ?: ""
                
                if (poster.isEmpty()) {
                    val relPoster = posterEl?.attr("data-src")?.takeIf { it.isNotEmpty() } ?: posterEl?.attr("src") ?: ""
                    if (relPoster.isNotEmpty()) {
                        poster = if (relPoster.startsWith("http")) relPoster else baseUrl.trimEnd('/') + (if (relPoster.startsWith("/")) "" else "/") + relPoster
                    }
                }
                
                if (poster.isEmpty()) {
                    if (index < 3) AppLogger.d(tag, "Item $index: poster empty for $rawTitle")
                    return@forEachIndexed
                }

                val title = cleanTitle(rawTitle)
                val year = element.selectFirst(".movie-year, .th-year, .year, .date, .desc, .info, .short-meta")?.text()?.let { 
                    Regex("""\b(19|20)\d{2}\b""").find(it)?.value 
                }
                val genres = element.select(".movie-genre a, .th-genre a, .genres a, .tags a, .short-meta a").map { it.text().trim() }

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
                        shortDescription = element.selectFirst(".movie-text, .th-desc, .story-text, .short-desc, .description, p, .short-desc")?.text()?.trim()
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(tag, "Error parsing movie item $index", e)
            }
        }
        AppLogger.i(tag, "Successfully parsed ${movies.size} movies")
        return movies.distinctBy { it.pageUrl }.filter { it.title.isNotEmpty() }
    }

    override suspend fun parseMovieDetails(doc: Document, url: String): MovieDetail {
        val rawTitle = doc.selectFirst("meta[property='og:title']")?.attr("content")
            ?: doc.selectFirst("h1, .full-title, .title-h1, .movie-title")?.text() ?: ""
        val title = cleanTitle(rawTitle)
        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")
            ?: doc.selectFirst(".movie-img img, .full-img img, .fullstory-img img, .poster img")?.absUrl("src") ?: ""
        val description = doc.selectFirst("meta[property='og:description']")?.attr("content")
            ?: doc.selectFirst(".fullstory-text, #full-story, .video-desc, .movie-desc, .description, .content")?.text()?.trim() ?: ""
        
        val yearEl = doc.selectFirst(".fi-label:contains(Рік виходу), .f-label:contains(Рік), .movie-info-item:contains(Рік), .year-info, .info-year")
        val year = yearEl?.parent()?.selectFirst(".deck-value, .f-desc, .info-desc, span")?.text()?.trim()
            ?: doc.selectFirst(".movie-year, .year")?.text()?.trim()
            
        val genreEl = doc.selectFirst(".fi-label:contains(Жанр), .f-label:contains(Жанр), .movie-info-item:contains(Жанр)")
        val genres = genreEl?.parent()?.select(".deck-value a, .f-desc a, .info-desc a, a")?.map { it.text().trim() }
            ?: doc.select(".movie-genres a, .genres a")?.map { it.text().trim() }
            ?: emptyList()

        return MovieDetail(
            id = url.substringAfterLast("/").substringBefore("-"),
            title = title,
            poster = poster,
            description = description,
            year = year,
            genres = genres,
            pageUrl = url,
            providerName = "Uakino",
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
                val response = uakinoApi.getPlaylist(newsId, xf, edittime)
                if (!response.success) continue
                
                val htmlToParse = response.response.replace("\\/", "/")
                if (htmlToParse.isBlank()) continue
                
                val doc = Jsoup.parse(htmlToParse)
                
                if (doc.select(".playlists-lists li").isNotEmpty()) return null
                
                val firstFile = doc.selectFirst("li[data-file], li[data-src], li[data-url]")?.let { li ->
                    li.attr("data-file").ifEmpty { li.attr("data-src") }.ifEmpty { li.attr("data-url") }
                }
                
                if (!firstFile.isNullOrBlank()) {
                    val finalUrl = if (firstFile.startsWith("//")) "https:$firstFile" else firstFile
                    return MediaSource.Movie(finalUrl, emptyList(), url, "Uakino")
                }
            } catch (e: Exception) {
                AppLogger.e(tag, "AJAX request failed in getStreamByNewsId", e)
            }
        }
        return null
    }
}