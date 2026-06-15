package ua.ukrtv.app.data.parsing

import org.jsoup.nodes.Document
import ua.ukrtv.app.data.model.ParsedMovie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.data.providers.MediaSource

import ua.ukrtv.app.util.ContentUtils

interface ContentParser {
    suspend fun parseMovies(doc: Document): List<ParsedMovie>
    suspend fun parseMovieDetails(doc: Document, url: String): MovieDetail
    suspend fun getStreamUrl(doc: Document, url: String): MediaSource?
    
    fun cleanTitle(title: String): String = ContentUtils.cleanTitle(title)
    fun inferContentType(url: String, title: String = ""): ua.ukrtv.app.domain.model.ContentType = 
        ContentUtils.inferContentType(url, title)
}
