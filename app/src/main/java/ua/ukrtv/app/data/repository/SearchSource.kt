package ua.ukrtv.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.providers.ContentCategory
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor

internal class SearchSource(
    private val providerManager: ProviderManager
) {
    private val popularCache = TtlLruCache<String, List<Movie>>(maxSize = 20, ttlMs = 15 * 60 * 1000L)
    private val searchCache = TtlLruCache<String, List<Movie>>(maxSize = 50, ttlMs = Constants.SEARCH_CACHE_TTL_MS)
    private val searchSemaphore = Semaphore(3)

    fun getPopularByCategory(category: ContentCategory): Flow<List<Movie>> = flow {
        val cacheKey = "popular|${providerManager.activeProvider.value.name}|$category"
        popularCache.get(cacheKey)?.let {
            emit(it)
            return@flow
        }

        try {
            val movies = providerManager.activeProvider.value.getMoviesByCategory(category)
            if (movies.isNotEmpty()) {
                popularCache.put(cacheKey, movies)
                emit(movies)
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    fun search(query: String): Flow<Result<List<Movie>>> = flow<Result<List<Movie>>> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            emit(Result.success(emptyList()))
            return@flow
        }

        val cacheKey = "search|all|$q"
        searchCache.get(cacheKey)?.let {
            emit(Result.success(it))
            return@flow
        }

        try {
            PerformanceMonitor.begin("SearchSource.search")
            val allMovies = mutableListOf<Movie>()

            coroutineScope {
                val deferred = providerManager.availableProviders.map { provider ->
                    async {
                        try {
                            val items = searchSemaphore.withPermit {
                                provider.search(q, limit = 40)
                            }
                            items.map { item ->
                                Movie(
                                    id = item.url,
                                    title = item.title,
                                    poster = item.imageUrl,
                                    pageUrl = item.url,
                                    provider = item.provider
                                )
                            }
                        } catch (_: Exception) { emptyList() }
                    }
                }
                deferred.awaitAll().forEach { allMovies.addAll(it) }
            }

            val movies = allMovies
                .groupBy { it.provider }
                .flatMap { (_, items) -> if (items.size >= 30) emptyList() else items }
                .distinctBy { it.pageUrl }

            if (movies.isNotEmpty()) {
                searchCache.put(cacheKey, movies)
                emit(Result.success(movies))
            } else {
                emit(Result.success(emptyList()))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        } finally {
            PerformanceMonitor.end()
        }
    }.flowOn(Dispatchers.IO)
}
