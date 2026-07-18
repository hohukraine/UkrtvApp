package ua.ukrtv.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.data.local.dao.CatalogIndexDao
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.providers.ContentCategory
import ua.ukrtv.app.data.providers.ContentUtils
import ua.ukrtv.app.data.providers.MediaProvider
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val providerManager: ProviderManager,
    private val watchProgressRepository: WatchProgressRepository,
    private val streamResolver: StreamResolver,
    private val htmlCacheDao: ua.ukrtv.app.data.local.dao.HtmlCacheDao,
    private val homeCacheRepository: HomeCacheRepository,
    private val catalogRepository: CatalogRepository,
    private val catalogDao: CatalogIndexDao
) {
    private val homeSource = HomeGridSource(homeCacheRepository)
    private val searchSource = SearchSource(providerManager, catalogRepository, catalogDao)
    private val detailSource = DetailSource(providerManager, streamResolver)
    private val trendsCache = TtlLruCache<String, List<Movie>>(maxSize = 5, ttlMs = 15 * 60 * 1000L)

    private var cleanupJob: kotlinx.coroutines.Job? = null

    init {
        cleanupOldCaches()
    }

    private fun cleanupOldCaches() {
        cleanupJob?.cancel()
        cleanupJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val now = System.currentTimeMillis()
                htmlCacheDao.deleteOldCache(now - (24 * 60 * 60 * 1000L))
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    AppLogger.e("ContentRepository", "Cache cleanup failed", e)
                }
            }
        }
    }

    suspend fun isHomeCacheStale(providerName: String, staleHoursThreshold: Long = 6): Boolean {
        val ts = homeCacheRepository.getCacheTimestamp(providerName)
        return (System.currentTimeMillis() - ts) / (60 * 60 * 1000L) >= staleHoursThreshold
    }

    fun shutdown() {
        cleanupJob?.cancel()
        cleanupJob = null
    }

    fun getHomeGrid(provider: MediaProvider): Flow<List<Movie>> =
        homeSource.getHomeGrid(provider)

    suspend fun getTrendsForGrid(): List<Movie> {
        val provider = providerManager.activeProvider.value
        val cacheKey = provider.name

        trendsCache.get(cacheKey)?.let { return it }

        val cachedSections = homeCacheRepository.getHomeCache(provider.name)
        val cachedItems = cachedSections?.firstOrNull()?.items.orEmpty()

        val newItems = coroutineScope {
            (1..4).map { page ->
                async(Dispatchers.IO) {
                    provider.getMoviesByCategory(ContentCategory.TRENDS, page)
                }
            }.awaitAll().flatten()
        }

        val merged = (newItems + cachedItems)
            .distinctBy { it.pageUrl }
            .shuffled()
            .take(100)

        homeCacheRepository.saveHomeCache(provider.name, listOf(HomeSection("Main", merged)))
        trendsCache.put(cacheKey, merged)
        return merged
    }

    private fun parseSeasonEpisode(episodeId: String?): Pair<Int?, Int?> {
        if (episodeId == null) return null to null
        val regex = Regex("""(?:s|season)[^\d]*(\d+)[^\d]*(?:e|ep|episode)[^\d]*(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(episodeId) ?: return null to null
        return match.groupValues[1].toIntOrNull() to match.groupValues[2].toIntOrNull()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getContinueWatching(): Flow<List<Movie>> = watchProgressRepository.getAllProgress()
        .mapLatest { allProgress ->
            allProgress
                .filter { it.progressPercentage in 1..94 }
                .sortedByDescending { it.timestamp }
                .distinctBy { ContentUtils.cleanTitle(it.title) }
                .mapNotNull { progress ->
                    val pUrl = progress.pageUrl
                    if (pUrl.isEmpty()) return@mapNotNull null
                    val (season, episode) = parseSeasonEpisode(progress.episodeId)
                    Movie(
                        id = progress.contentId,
                        title = ContentUtils.cleanTitle(progress.title),
                        poster = progress.poster,
                        pageUrl = pUrl,
                        watchProgress = progress.progressPercentage,
                        contentType = if (season != null || progress.episodeId != null) "СЕРІАЛ" else null,
                        season = season,
                        episode = episode
                    )
                }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun removeFromContinueWatching(movie: Movie) {
        watchProgressRepository.deleteProgress(movie.id)
    }

    fun getPopularByCategory(category: ContentCategory): Flow<List<Movie>> =
        searchSource.getPopularByCategory(category)

    fun search(query: String): Flow<Result<List<Movie>>> =
        searchSource.search(query)

    fun getDetails(id: String, url: String, alternateUrl: String? = null): Flow<Result<MovieDetail>> =
        detailSource.getDetails(id, url, alternateUrl)

    suspend fun getStream(url: String, season: Int?, episode: Int?): StreamResolutionResult? {
        PerformanceMonitor.begin("ContentRepo.getStream")
        try {
            val res = withTimeout(Constants.STREAM_RESOLUTION_TIMEOUT_MS) {
                streamResolver.resolve(url, season = season, episode = episode)
            }
            return res
        } catch (e: Exception) {
            AppLogger.w("ContentRepository", "getStream failed: ${e.message}")
            return null
        } finally {
            PerformanceMonitor.end()
        }
    }

    suspend fun enrichSeasons(url: String, detail: MovieDetail): MovieDetail =
        detailSource.enrichSeasons(url, detail)
}
