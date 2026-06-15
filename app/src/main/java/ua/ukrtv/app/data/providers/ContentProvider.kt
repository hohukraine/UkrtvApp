package ua.ukrtv.app.data.providers

import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.model.HomeSection

interface ContentProvider : StreamProvider {
    val brandColor: String
    val logoUrl: String
    suspend fun getHomeSections(page: Int = 1): List<HomeSection>
    suspend fun getPopular(category: ContentCategory, page: Int = 1): List<Movie>
    override suspend fun search(query: String, limit: Int): List<SearchItem>
    suspend fun getDetails(id: String, url: String): MovieDetail
}

enum class ContentCategory {
    MOVIES, SERIES, ANIME, CARTOONS
}
