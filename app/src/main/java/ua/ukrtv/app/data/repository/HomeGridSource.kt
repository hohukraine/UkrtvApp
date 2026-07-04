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
        val cacheAge = homeCacheRepository.getCacheTimestamp(providerName)
        val now = System.currentTimeMillis()
        val cacheTTL = 24 * 60 * 60 * 1000L

        val cachedSections = homeCacheRepository.getHomeCache(providerName).orEmpty()
        val cached = cachedSections.firstOrNull()?.items
        if (cacheAge > 0 && (now - cacheAge) < cacheTTL && cachedSections.isNotEmpty() && cached != null && cached.isNotEmpty()) {
            val firstUrl = cached.firstOrNull()?.pageUrl ?: ""
            val providerHost = provider.baseUrl.substringAfter("://").substringBefore("/")
            if (firstUrl.isEmpty() || firstUrl.contains(providerHost, ignoreCase = true)) {
                emit(cached)
                AppLogger.d("ContentRepo", "Cache hit for ${providerName}, fetching fresh in background")
            }
        }

        try {
            val t = System.currentTimeMillis()

            val page1 = fetchCategoryWithFallback(provider, ContentCategory.TRENDS, 1)
                .distinctBy { it.pageUrl }

            AppLogger.d("ContentRepo", "Page 1 fetched: ${page1.size} items")
            emit(page1)

            if (page1.isNotEmpty()) {
                val seen = mutableSetOf<String>()
                seen.addAll(page1.map { it.pageUrl })

                var accumulated = page1

                supervisorScope {
                    (2..4).map { page ->
                        async {
                            try {
                                withTimeout(10_000) {
                                    val items = fetchCategoryWithFallback(provider, ContentCategory.TRENDS, page)
                                    items to page
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                AppLogger.w("ContentRepo", "Page $page fetch failed: ${e.message}")
                                emptyList<Movie>() to page
                            }
                        }
                    }.forEach { deferred ->
                        val (items, _) = deferred.await()
                        val newItems = items.filter { seen.add(it.pageUrl) }
                        if (newItems.isNotEmpty()) {
                            accumulated = (accumulated + newItems).take(150)
                            emit(accumulated)
                        }
                    }
                }

                if (accumulated.size > page1.size || accumulated.size >= 150) {
                    homeCacheRepository.saveHomeCache(providerName, listOf(HomeSection("Main", accumulated)))
                }
                AppLogger.perf("ContentRepo", "Home grid deep fetch (${provider.name}) total ${accumulated.size} items", t)
            } else {
                homeCacheRepository.saveEmptyCache(providerName)
            }
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
