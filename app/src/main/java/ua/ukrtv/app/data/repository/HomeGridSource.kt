package ua.ukrtv.app.data.repository

import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import ua.ukrtv.app.data.providers.MediaProvider
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PosterColorCache

internal class HomeGridSource(
    private val homeCacheRepository: HomeCacheRepository
) {
    fun getHomeGrid(provider: MediaProvider, forceRefresh: Boolean = false): Flow<List<Movie>> = flow {
        val cachedSections = homeCacheRepository.getHomeCache(provider.name)
        val cached = cachedSections?.firstOrNull()?.items
        
        if (!cached.isNullOrEmpty()) {
            emit(cached.map { it.withCachedColor().copy(provider = it.provider ?: provider.name) })
        }

        val stale = forceRefresh || homeCacheRepository.isHomeCacheStale(provider.name)
        if (!stale) return@flow

        // Fetch fresh data only when the cache is stale (or explicitly forced)
        try {
            val freshItems = provider.getHomeSections()
                .flatMap { it.items }
                .map { it.copy(provider = it.provider ?: provider.name) }
                .distinctBy { it.pageUrl }
                .take(50)
            
            if (freshItems.isNotEmpty()) {
                val mappedItems = freshItems.map { it.withCachedColor() }
                emit(mappedItems)
                homeCacheRepository.saveHomeCache(
                    provider.name,
                    listOf(HomeSection("Main", freshItems))
                )
            } else if (cached.isNullOrEmpty()) {
                emit(emptyList())
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                AppLogger.e("HomeGridSource", "Network fetch failed", e)
            }
            if (cached.isNullOrEmpty()) emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    private fun Movie.withCachedColor(): Movie {
        val cached = PosterColorCache.getCached(this.poster) ?: return this
        val hex = String.format("#%06X", (0xFFFFFF and cached.toArgb()))
        return this.copy(brandColor = hex)
    }
}
