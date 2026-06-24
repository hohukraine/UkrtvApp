package ua.ukrtv.app.data.providers

import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail

interface MediaProvider {
    val name: String
    val baseUrl: String
    val id: String
    val brandColor: String
    val logoUrl: String

    fun getHomeCategories(): List<ContentCategory>
    suspend fun initializeSession(): Boolean
    suspend fun getHomeSections(page: Int = 1): List<HomeSection>
    suspend fun getMoviesByCategory(category: ContentCategory, page: Int = 1): List<Movie>
    suspend fun search(query: String, limit: Int = 10): List<SearchItem>
    suspend fun getMovieDetails(url: String): MovieDetail
    suspend fun getMediaSource(pageUrl: String, season: Int? = null, episode: Int? = null, isDeep: Boolean = true): MediaSource?
    fun supportsUrl(url: String): Boolean
    fun clearCache(url: String? = null)
}
