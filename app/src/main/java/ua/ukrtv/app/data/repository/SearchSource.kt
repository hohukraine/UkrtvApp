package ua.ukrtv.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.data.local.dao.CatalogIndexDao
import ua.ukrtv.app.data.local.entity.CatalogIndexEntity
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.providers.ContentCategory
import ua.ukrtv.app.util.PerformanceMonitor

internal class SearchSource(
    private val providerManager: ProviderManager,
    private val catalogDao: CatalogIndexDao
) {
    private val popularCache = TtlLruCache<String, List<Movie>>(maxSize = 20, ttlMs = 15 * 60 * 1000L)
    private val searchCache = TtlLruCache<String, List<Movie>>(maxSize = 50, ttlMs = Constants.SEARCH_CACHE_TTL_MS)

    fun getPopularByCategory(category: ContentCategory): Flow<List<Movie>> = flow {
        val cacheKey = "popular|all|$category"
        popularCache.get(cacheKey)?.let {
            emit(it)
            return@flow
        }

        val merged = coroutineScope {
            providerManager.availableProviders.map { provider ->
                async(Dispatchers.IO) {
                    try {
                        provider.getMoviesByCategory(category)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten().distinctBy { it.pageUrl }
        }

        if (merged.isNotEmpty()) {
            popularCache.put(cacheKey, merged)
            emit(merged)
        } else {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    fun search(query: String): Flow<Result<List<Movie>>> = flow<Result<List<Movie>>> {
        val q = query.trim().lowercase()
            .replace(Regex("[,:;—–\\-\"]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
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
            val entities = searchCatalog(q)
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

    private suspend fun searchCatalog(query: String): List<CatalogIndexEntity> {
        val words = query.split("\\s+".toRegex()).filter { it.length >= 2 }

        if (words.isEmpty()) {
            return catalogDao.search(query, limit = 40)
        }

        val results = when (words.size) {
            1 -> catalogDao.search(words[0], limit = 40)
            2 -> catalogDao.searchTwoWords(words[0], words[1], limit = 40)
            3 -> catalogDao.searchThreeWords(words[0], words[1], words[2], limit = 40)
            4 -> catalogDao.searchFourWords(words[0], words[1], words[2], words[3], limit = 40)
            5 -> catalogDao.searchFiveWords(words[0], words[1], words[2], words[3], words[4], limit = 40)
            else -> catalogDao.searchSixWords(words[0], words[1], words[2], words[3], words[4], words[5], limit = 40)
        }

        if (results.isNotEmpty()) return results

        return catalogDao.search(query, limit = 40)
    }
}
