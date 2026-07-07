package ua.ukrtv.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout
import ua.ukrtv.app.Constants
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.providers.ContentCategory
import ua.ukrtv.app.data.providers.ContentUtils
import ua.ukrtv.app.data.providers.MediaProvider
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor
import ua.ukrtv.app.util.SearchScorer
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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getContinueWatching(): Flow<List<Movie>> = watchProgressRepository.getAllProgress()
        .mapLatest { allProgress ->
            allProgress
                .filter { it.progressPercentage in 1..94 }
                .sortedByDescending { it.timestamp }
                .distinctBy { ContentUtils.cleanTitle(it.title).lowercase() }
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

    suspend fun resolveTop200(movie: Top200Movie): Movie? {
        val queries = buildList {
            if (movie.originalTitle.isNotBlank()) add(movie.originalTitle)
            if (movie.title.isNotBlank()) add(movie.title)
            movie.year.toIntOrNull()?.let { year ->
                if (movie.originalTitle.isNotBlank()) add("${movie.originalTitle} $year")
                if (movie.title.isNotBlank()) add("${movie.title} $year")
            }
            addAll(movie.searchQueries)
        }.distinct()

        val scoringQueries = listOfNotNull(movie.originalTitle, movie.title) + movie.searchQueries
        val expectedYear = movie.year.toIntOrNull()

        val active = providerManager.activeProvider.value
        val other = providerManager.availableProviders.find { it.name != active.name }

        return searchProviderBest(active, queries, scoringQueries, expectedYear)
            ?: other?.let { searchProviderBest(it, queries, scoringQueries, expectedYear) }
    }

    private suspend fun searchProviderBest(
        provider: MediaProvider,
        searchQueries: List<String>,
        scoringQueries: List<String>,
        expectedYear: Int?
    ): Movie? {
        val allResults = mutableListOf<Movie>()

        coroutineScope {
            val deferred = searchQueries.map { query ->
                async {
                    try {
                        provider.search(query, limit = 40).map { item ->
                            Movie(
                                id = item.url,
                                title = item.title,
                                poster = item.imageUrl,
                                pageUrl = item.url,
                                year = item.year?.toIntOrNull(),
                                provider = provider.name
                            )
                        }
                    } catch (_: Exception) { emptyList() }
                }
            }
            val results = withTimeoutOrNull(20_000L) { deferred.awaitAll() }.orEmpty()
            allResults.addAll(results.flatMap { it })
        }

        val uniqueResults = allResults.distinctBy { it.pageUrl }
        return SearchScorer.pickBestMatch(
            results = uniqueResults,
            queries = scoringQueries,
            expectedYear = expectedYear
        )
    }
}
