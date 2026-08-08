package ua.ukrtv.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.providers.ContentCategory
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor

internal class SearchSource(
    private val providerManager: ProviderManager,
    private val catalogRepository: CatalogRepository
) {
    private val popularCache = TtlLruCache<String, List<Movie>>(maxSize = 20, ttlMs = 15 * 60 * 1000L)
    private val searchCache = TtlLruCache<String, List<Movie>>(maxSize = 50, ttlMs = Constants.SEARCH_CACHE_TTL_MS)

    fun getPopularByCategory(category: ContentCategory): Flow<List<Movie>> = flow {
        val provider = providerManager.activeProvider.value
        val cacheKey = "popular|${provider.name}|$category"
        popularCache.get(cacheKey)?.let {
            emit(it)
            return@flow
        }

        val merged = try {
            provider.getMoviesByCategory(category).map { it.copy(provider = it.provider ?: provider.name) }
        } catch (_: Exception) {
            emptyList()
        }

        if (merged.isNotEmpty()) {
            popularCache.put(cacheKey, merged)
            emit(merged)
        } else {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    fun search(query: String): Flow<Result<List<Movie>>> = flow<Result<List<Movie>>> {
        val q = query.trim().lowercase(java.util.Locale.ROOT)
            .replace(Regex("[,:;—–\\-\"]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (q.isEmpty()) {
            emit(Result.success(emptyList()))
            return@flow
        }

        val provider = providerManager.activeProvider.value
        val cacheKey = "search|${provider.name}|$q"
        searchCache.get(cacheKey)?.let {
            emit(Result.success(it))
            return@flow
        }

        try {
            PerformanceMonitor.begin("SearchSource.search")
            catalogRepository.awaitProviderReady(provider.name)
            val exact = catalogRepository.searchByProviderWordsExact(provider.name, q, limit = 100)
            val entities = if (exact.isNotEmpty()) exact else catalogRepository.searchByProviderWords(provider.name, q, limit = 100)
            AppLogger.d("SearchSource", "Search for '$q' on ${provider.name} found ${entities.size} items")
            val movies = entities.map { entity ->
                Movie(
                    id = entity.url,
                    title = entity.title,
                    poster = entity.poster,
                    pageUrl = entity.url,
                    provider = entity.provider,
                    rating = entity.rating.ifEmpty { null },
                    year = entity.year.toIntOrNull(),
                    quality = entity.quality.ifEmpty { null },
                    contentType = entity.contentType.ifEmpty { null }
                )
            }.distinctBy { it.pageUrl }

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
