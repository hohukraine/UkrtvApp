package ua.ukrtv.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.data.local.dao.SeriesStructureDao
import ua.ukrtv.app.data.local.entity.SeriesStructureEntity
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.domain.model.deserializeSeasons
import ua.ukrtv.app.domain.model.serializeSeasons
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor
import ua.ukrtv.app.matching.SearchScorer

internal class DetailSource(
    private val providerManager: ProviderManager,
    private val streamResolver: StreamResolver,
    private val seriesStructureDao: SeriesStructureDao,
    private val seriesIndexRepository: SeriesIndexRepository
) {
    private val metadataCache = TtlLruCache<String, MovieDetail>(maxSize = 200, ttlMs = Constants.METADATA_CACHE_TTL_MS)
    private val navigationCache = TtlLruCache<String, MovieDetail>(maxSize = 100, ttlMs = 60 * 60 * 1000L)
    private val detailFetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingDetailFetches = ConcurrentHashMap<String, Deferred<MovieDetail>>()
    private val structureRefreshes = ConcurrentHashMap<String, Boolean>()

    fun getDetails(id: String, url: String, alternateUrl: String? = null): Flow<Result<MovieDetail>> = flow<Result<MovieDetail>> {
        val getT = System.currentTimeMillis()
        try {
            PerformanceMonitor.begin("DetailSource.getDetails")
            val result = fetchDetails(url)
            if (result.isSuccess || alternateUrl == null) {
                emit(result)
                return@flow
            }
            AppLogger.d("ContentRepo", "Primary provider failed for $url, trying alternate: $alternateUrl")
            emit(fetchDetails(alternateUrl))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("ContentRepository", "Fatal error in getDetails: ${e.message}", e)
            emit(Result.failure(e))
        } finally {
            PerformanceMonitor.end()
        }
        AppLogger.perf("ContentRepository", "getDetails total ($url)", getT)
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchDetails(url: String): Result<MovieDetail> {
        val targetProvider = providerManager.getProviderForUrl(url) ?: providerManager.activeProvider.value
        val providerName = targetProvider.name
        AppLogger.d("ContentRepo", "Routing detail request for $url to provider: $providerName")

        val cacheKey = "details_pure|$providerName|$url"

        navigationCache.get(cacheKey)?.let {
            AppLogger.d("ContentRepository", "Detail cache HIT (nav) for $url")
            return Result.success(it)
        }

        metadataCache.get(cacheKey)?.let {
            AppLogger.d("ContentRepository", "Detail cache HIT (meta) for $url")
            navigationCache.put(cacheKey, it)
            return Result.success(it)
        }

        if (url.isEmpty()) {
            return Result.failure(Exception("URL порожній"))
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
        return Result.success(detail)
    }

    suspend fun enrichSeasons(url: String, detail: MovieDetail): MovieDetail {
        val providerName = providerManager.getProviderForUrl(url)?.name ?: providerManager.activeProvider.value.name
        val cacheKey = "details_pure|$providerName|$url"

        val cached = readStructureCache(url)
        val cachedSeasons = cached?.let { deserializeSeasons(it.seasonsJson).takeIf { s -> s.isNotEmpty() } }
        val cacheAge = cached?.let { System.currentTimeMillis() - it.updatedAt }

        // A cache holding FEWER episodes than the precomputed index (e.g. written before the slug
        // was indexed) is partial: keep serving it would hide episodes that the offline index can
        // now produce for free. Re-resolve fresh instead (index fast path = 0 HTTP).
        val indexSlug = SeriesIndexRepository.uaflixSlugFromUrl(url)
        val indexedCount = indexSlug?.let { seriesIndexRepository.indexEpisodeCount(it) }
        val cachedCount = cachedSeasons?.sumOf { s -> s.voiceovers.sumOf { v -> v.episodes.size } }
        val cacheComplete = SeriesStructureCompleteness.isCacheComplete(indexedCount, cachedCount)
        if (!cacheComplete) {
            AppLogger.d("ContentRepository", "Series structure cache INCOMPLETE for $url (cached=$cachedCount, index=$indexedCount), re-resolving")
        }

        if (cacheComplete && cachedSeasons != null && cacheAge != null && cacheAge < Constants.SERIES_STRUCTURE_CACHE_TTL_MS) {
            AppLogger.d("ContentRepository", "Series structure cache HIT (fresh) for $url (${cachedSeasons.size} seasons)")
            return cacheEnriched(detail, cacheKey, cachedSeasons)
        }

        if (cacheComplete && cachedSeasons != null && cacheAge != null && cacheAge < Constants.SERIES_STRUCTURE_CACHE_STALE_TTL_MS) {
            AppLogger.d("ContentRepository", "Series structure cache HIT (stale) for $url, refreshing in background")
            refreshStructureInBackground(url)
            return cacheEnriched(detail, cacheKey, cachedSeasons)
        }

        val resolution = try {
            withTimeout(Constants.STREAM_ENRICH_TIMEOUT_MS) {
                streamResolver.resolve(url, isDeep = true, timeoutMs = Constants.STREAM_ENRICH_TIMEOUT_MS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("ContentRepository", "Failed to enrich seasons: ${e.message}")
            null
        }

        if (resolution?.seasons != null && resolution.seasons.isNotEmpty()) {
            writeStructureCache(url, providerName, resolution.seasons)
            return cacheEnriched(detail, cacheKey, resolution.seasons)
        }

        // Resolution failed — fall back to whatever stale structure we have (better than nothing).
        if (cachedSeasons != null) {
            AppLogger.d("ContentRepository", "Structure resolve failed, falling back to stale cache for $url")
            return cacheEnriched(detail, cacheKey, cachedSeasons)
        }

        try {
            val opposite = providerManager.getOppositeProvider(url)
            if (opposite != null) {
                AppLogger.d("ContentRepository", "Primary provider returned no seasons, trying opposite: ${opposite.name}")
                val query = SearchScorer.cleanSearchQuery(detail.title)
                val results = opposite.search(query, limit = 10)

                // Never enrich seasons from an entry whose title is paired with a neighbour
                // card's URL (loose list scans). Only accept a confident same-movie match.
                val candidates = results.mapNotNull { item ->
                    if (SearchScorer.titleSlugConsistency(item.title, item.url) < MIN_SLUG_CONSISTENCY) {
                        AppLogger.w("ContentRepository", "Rejected mismatched season candidate '${item.title}' @ ${item.url}")
                        null
                    } else {
                        Movie(
                            id = item.url,
                            title = item.title,
                            poster = item.imageUrl,
                            pageUrl = item.url,
                            year = item.year?.toIntOrNull(),
                            provider = item.provider
                        )
                    }
                }

                val match = SearchScorer.pickBestMatch(candidates, listOf(query), detail.year)
                if (match != null) {
                    AppLogger.d("ContentRepository", "Cross-provider season match: '${match.title}' @ ${match.pageUrl}")
                    val oppResolution = withTimeout(Constants.STREAM_ENRICH_TIMEOUT_MS) {
                        streamResolver.resolve(match.pageUrl, isDeep = true, timeoutMs = Constants.STREAM_ENRICH_TIMEOUT_MS)
                    }
                    if (oppResolution?.seasons != null && oppResolution.seasons.isNotEmpty()) {
                        writeStructureCache(url, providerName, oppResolution.seasons)
                        return cacheEnriched(detail, cacheKey, oppResolution.seasons)
                    }
                } else {
                    AppLogger.w("ContentRepository", "No confident season match for '${detail.title}' on ${opposite.name}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("ContentRepository", "Cross-provider season enrichment failed: ${e.message}")
        }

        return detail
    }

    private fun cacheEnriched(detail: MovieDetail, cacheKey: String, seasons: List<Season>): MovieDetail {
        val enriched = detail.copy(seasons = seasons)
        metadataCache.put(cacheKey, enriched)
        navigationCache.put(cacheKey, enriched)
        return enriched
    }

    private fun refreshStructureInBackground(url: String) {
        if (structureRefreshes.putIfAbsent(url, true) != null) return
        detailFetchScope.launch {
            try {
                val resolution = withTimeout(Constants.STREAM_ENRICH_TIMEOUT_MS) {
                    streamResolver.resolve(url, isDeep = true, timeoutMs = Constants.STREAM_ENRICH_TIMEOUT_MS)
                }
                if (resolution?.seasons != null && resolution.seasons.isNotEmpty()) {
                    val providerName = providerManager.getProviderForUrl(url)?.name ?: ""
                    writeStructureCache(url, providerName, resolution.seasons)
                    AppLogger.d("ContentRepository", "Background structure refresh updated $url")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("ContentRepository", "Background structure refresh failed: ${e.message}")
            } finally {
                structureRefreshes.remove(url)
            }
        }
    }

    private suspend fun writeStructureCache(url: String, providerName: String, seasons: List<Season>) = withContext(Dispatchers.IO) {
        try {
            seriesStructureDao.upsert(
                SeriesStructureEntity(
                    url = url,
                    seasonsJson = serializeSeasons(seasons),
                    provider = providerName,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("ContentRepository", "Failed to write series structure cache: ${e.message}")
        }
    }

    private suspend fun readStructureCache(url: String): SeriesStructureEntity? = withContext(Dispatchers.IO) {
        try { seriesStructureDao.get(url) } catch (_: Exception) { null }
    }

    companion object {
        private const val MIN_SLUG_CONSISTENCY = 0.35f
    }
}
