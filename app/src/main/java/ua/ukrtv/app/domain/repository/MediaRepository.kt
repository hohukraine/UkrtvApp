package ua.ukrtv.app.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.model.StreamResolutionResult

interface MediaRepository {
    fun getHomeSections(): Flow<Result<List<HomeSection>>>
    fun getBannerMovie(): Flow<Movie?>
    fun getContinueWatching(): Flow<List<Movie>>
    fun getPopularByCategory(category: ua.ukrtv.app.data.providers.ContentCategory): Flow<List<Movie>>
    fun search(query: String): Flow<Result<List<Movie>>>
    fun getDetails(id: String, url: String): Flow<Result<MovieDetail>>
    suspend fun getStream(url: String): StreamResolutionResult?
}

