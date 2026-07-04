package ua.ukrtv.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout
import ua.ukrtv.app.Constants
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.model.StreamResolutionResult
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
    private val homeCacheRepository: HomeCacheRepository
) {
    private val homeSource = HomeGridSource(providerManager, homeCacheRepository)
    private val searchSource = SearchSource(providerManager)
    private val detailSource = DetailSource(providerManager, streamResolver)

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

    fun shutdown() {
        cleanupJob?.cancel()
        cleanupJob = null
    }

    fun getHomeGrid(provider: MediaProvider): Flow<List<Movie>> =
        homeSource.getHomeGrid(provider)

    private fun parseSeasonEpisode(episodeId: String?): Pair<Int?, Int?> {
        if (episodeId == null) return null to null
        val regex = Regex("""(?:s|season)[^\d]*(\d+)[^\d]*(?:e|ep|episode)[^\d]*(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(episodeId) ?: return null to null
        return match.groupValues[1].toIntOrNull() to match.groupValues[2].toIntOrNull()
    }

    fun getContinueWatching(): Flow<List<Movie>> = watchProgressRepository.getAllProgress()
        .mapLatest { allProgress ->
            allProgress
                .filter { it.progressPercentage in 1..94 }
                .distinctBy { it.pageUrl }
                .mapNotNull { progress ->
                    val pUrl = progress.pageUrl
                    if (pUrl.isEmpty() || !providerManager.activeProvider.value.supportsUrl(pUrl)) return@mapNotNull null
                    val (season, episode) = parseSeasonEpisode(progress.episodeId)
                    Movie(
                        id = progress.contentId,
                        title = ContentUtils.cleanTitle(progress.title ?: ""),
                        poster = progress.poster.orEmpty(),
                        pageUrl = pUrl,
                        watchProgress = progress.progressPercentage,
                        contentType = if (season != null) "СЕРІАЛ" else null,
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

    fun getDetails(id: String, url: String): Flow<Result<MovieDetail>> =
        detailSource.getDetails(id, url)

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
