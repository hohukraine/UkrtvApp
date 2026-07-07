package ua.ukrtv.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.providers.ContentCategory
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor

internal class HomeGridSource(
    private val providerManager: ProviderManager,
    private val homeCacheRepository: HomeCacheRepository
) {
    fun getHomeGrid(provider: ua.ukrtv.app.data.providers.MediaProvider): Flow<List<Movie>> = flow {
        PerformanceMonitor.begin("HomeGridSource.getHomeGrid")
        val providerName = provider.name

        val cachedSections = homeCacheRepository.getHomeCache(providerName).orEmpty()
        val cached = cachedSections.firstOrNull()?.items
        if (cached != null && cached.isNotEmpty()) {
            emit(cached)
            val cacheAge = System.currentTimeMillis() - homeCacheRepository.getCacheTimestamp(providerName)
            if (cacheAge < Constants.HOME_CACHE_TTL_MS) {
                AppLogger.d("ContentRepo", "Cache fresh (${cacheAge}ms old), skipping network fetch")
                return@flow
            }
            AppLogger.d("ContentRepo", "Cache stale (${cacheAge}ms old), refreshing from network")
        }

        try {
            val t = System.currentTimeMillis()

            val allPages = coroutineScope {
                (1..4).map { page ->
                    async(Dispatchers.IO) {
                        fetchCategoryWithFallback(provider, ContentCategory.TRENDS, page)
                    }
                }.awaitAll().flatten()
            }

            if (allPages.isEmpty()) {
                homeCacheRepository.saveEmptyCache(providerName)
                return@flow
            }

            val merged = (allPages + cached.orEmpty())
                .distinctBy { it.pageUrl }
                .take(150)

            AppLogger.d("ContentRepo", "Fetched ${allPages.size} items from 4 pages, merged = ${merged.size}")
            emit(merged)
            homeCacheRepository.saveHomeCache(providerName, listOf(HomeSection("Main", merged)))

            AppLogger.perf("ContentRepo", "Home grid sync (${provider.name}) total", t)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("ContentRepo", "Failed to fetch home grid", e)
        } finally {
            PerformanceMonitor.end()
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private suspend fun fetchCategoryWithFallback(provider: ua.ukrtv.app.data.providers.MediaProvider, category: ContentCategory, page: Int): List<Movie> {
        PerformanceMonitor.begin("fetchCategory:$category:$page")
        try {
            val result = provider.getMoviesByCategory(category, page)
            if (result.isNotEmpty() || category != ContentCategory.TRENDS) return result
            val movies = provider.getMoviesByCategory(ContentCategory.MOVIES, 1)
            if (movies.isNotEmpty()) return movies
            return provider.getMoviesByCategory(ContentCategory.TRENDS, 1)
        } finally {
            PerformanceMonitor.end()
        }
    }
}
