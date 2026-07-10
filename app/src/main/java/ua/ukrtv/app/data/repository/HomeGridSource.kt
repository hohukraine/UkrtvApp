package ua.ukrtv.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import ua.ukrtv.app.data.providers.MediaProvider
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.util.AppLogger

internal class HomeGridSource(
    private val homeCacheRepository: HomeCacheRepository
) {
    fun getHomeGrid(provider: MediaProvider): Flow<List<Movie>> = flow {
        val cachedSections = homeCacheRepository.getHomeCache(provider.name)
        val cached = cachedSections?.firstOrNull()?.items
        if (!cached.isNullOrEmpty()) {
            emit(cached)
        } else {
            try {
                val freshItems = provider.getHomeSections()
                    .flatMap { it.items }
                    .distinctBy { it.pageUrl }
                    .take(50)
                if (freshItems.isNotEmpty()) {
                    val firstBatch = freshItems.take(15)
                    emit(firstBatch)
                    if (freshItems.size > 15) {
                        delay(300)
                        emit(freshItems)
                    }
                    homeCacheRepository.saveHomeCache(
                        provider.name,
                        listOf(HomeSection("Main", freshItems))
                    )
                } else {
                    emit(emptyList())
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    AppLogger.e("HomeGridSource", "Network fallback failed", e)
                }
                emit(emptyList())
            }
        }
    }.flowOn(Dispatchers.IO)
}
