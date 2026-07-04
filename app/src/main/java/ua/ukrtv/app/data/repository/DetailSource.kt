package ua.ukrtv.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor

internal class DetailSource(
    private val providerManager: ProviderManager,
    private val streamResolver: StreamResolver
) {
    private val metadataCache = TtlLruCache<String, MovieDetail>(maxSize = 200, ttlMs = Constants.METADATA_CACHE_TTL_MS)
    private val navigationCache = TtlLruCache<String, MovieDetail>(maxSize = 100, ttlMs = 60 * 60 * 1000L)
    private val detailFetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingDetailFetches = ConcurrentHashMap<String, Deferred<MovieDetail>>()

    fun getDetails(id: String, url: String): Flow<Result<MovieDetail>> = flow<Result<MovieDetail>> {
        val getT = System.currentTimeMillis()
        try {
            PerformanceMonitor.begin("DetailSource.getDetails")
            val targetProvider = providerManager.getProviderForUrl(url) ?: providerManager.activeProvider.value
            val providerName = targetProvider.name
            AppLogger.d("ContentRepo", "Routing detail request for $url to provider: $providerName")

            val cacheKey = "details_pure|$providerName|$url"

            navigationCache.get(cacheKey)?.let {
                AppLogger.d("ContentRepository", "Detail cache HIT (nav) for $url")
                emit(Result.success(it))
                return@flow
            }

            metadataCache.get(cacheKey)?.let {
                AppLogger.d("ContentRepository", "Detail cache HIT (meta) for $url")
                emit(Result.success(it))
                navigationCache.put(cacheKey, it)
                return@flow
            }

            if (url.isEmpty()) {
                emit(Result.failure<MovieDetail>(Exception("URL порожній")))
                return@flow
            }

            AppLogger.d("ContentRepository", "Fetching details directly from provider ($providerName): $url")
            val fetchT = System.currentTimeMillis()

            val deferred = pendingDetailFetches[cacheKey] ?: synchronized(pendingDetailFetches) {
                pendingDetailFetches[cacheKey] ?: detailFetchScope.async(CoroutineName("detail-fetch-$url")) {
                    targetProvider.getMovieDetails(url)
                }.also { pendingDetailFetches[cacheKey] = it }
            }
            val detail = try {
                deferred.await()
            } catch (e: Exception) {
                pendingDetailFetches.remove(cacheKey)
                throw e
            }
            pendingDetailFetches.remove(cacheKey)

            AppLogger.perf("ContentRepository", "getMovieDetails parse", fetchT)

            metadataCache.put(cacheKey, detail)
            navigationCache.put(cacheKey, detail)
            emit(Result.success(detail))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("ContentRepository", "Fatal error in getDetails: ${e.message}", e)
            emit(Result.failure(e))
        } finally {
            PerformanceMonitor.end()
        }
        AppLogger.perf("ContentRepository", "getDetails total ($url)", getT)
    }.flowOn(Dispatchers.IO)

    suspend fun enrichSeasons(url: String, detail: MovieDetail): MovieDetail {
        val providerName = providerManager.getProviderForUrl(url)?.name ?: providerManager.activeProvider.value.name
        val cacheKey = "details_pure|$providerName|$url"
        try {
            val resolution = withTimeout(Constants.STREAM_RESOLUTION_TIMEOUT_MS) {
                streamResolver.resolve(url)
            }
            if (resolution?.seasons != null && resolution.seasons.isNotEmpty()) {
                val enriched = detail.copy(seasons = resolution.seasons)
                metadataCache.put(cacheKey, enriched)
                navigationCache.put(cacheKey, enriched)
                return enriched
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("ContentRepository", "Failed to enrich seasons: ${e.message}")
        }
        return detail
    }
}
