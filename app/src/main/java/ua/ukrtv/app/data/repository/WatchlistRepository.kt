package ua.ukrtv.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.ukrtv.app.data.local.dao.WatchlistDao
import ua.ukrtv.app.data.local.entity.WatchlistEntity
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.tv.TvRecommendationManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepository @Inject constructor(
    private val watchlistDao: WatchlistDao,
    private val tvRecommendationManager: TvRecommendationManager
) {
    suspend fun addToWatchlist(movie: Movie) {
        watchlistDao.insert(
            WatchlistEntity(
                contentId = movie.id,
                title = movie.title,
                poster = movie.poster,
                pageUrl = movie.pageUrl,
                contentType = movie.contentType,
                rating = movie.rating,
                year = movie.year
            )
        )
        try {
            tvRecommendationManager.publishWatchNext(movie)
        } catch (_: Exception) { }
    }

    suspend fun removeFromWatchlist(contentId: String) {
        watchlistDao.delete(contentId)
    }

    suspend fun isInWatchlist(contentId: String): Boolean {
        return watchlistDao.isInWatchlist(contentId)
    }

    fun getAllWatchlist(): Flow<List<WatchlistEntity>> {
        return watchlistDao.getAllWatchlist()
    }

    fun getAllWatchlistAsMovies(): Flow<List<Movie>> {
        return watchlistDao.getAllWatchlist().map { list ->
            list.map { entity ->
                Movie(
                    id = entity.contentId,
                    title = entity.title,
                    poster = entity.poster,
                    pageUrl = entity.pageUrl,
                    rating = entity.rating,
                    year = entity.year,
                    contentType = entity.contentType
                )
            }
        }
    }

    suspend fun getAllWatchlistIds(): List<String> {
        return watchlistDao.getAllWatchlistIds()
    }
}
