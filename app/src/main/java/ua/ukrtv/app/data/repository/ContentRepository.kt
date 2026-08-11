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
import ua.ukrtv.app.data.tmdb.TmdbTrendsRepository
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
    private val seriesStructureDao: ua.ukrtv.app.data.local.dao.SeriesStructureDao,
    private val seriesIndexRepository: SeriesIndexRepository,
    private val homeCacheRepository: HomeCacheRepository,
    private val catalogRepository: CatalogRepository,
    private val tmdbTrendsRepository: TmdbTrendsRepository
) {
    private val homeSource = HomeGridSource(homeCacheRepository)
    private val searchSource = SearchSource(providerManager, catalogRepository)
    private val detailSource = DetailSource(providerManager, streamResolver, seriesStructureDao, seriesIndexRepository)

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
                seriesStructureDao.deleteOlderThan(now - Constants.SERIES_STRUCTURE_CACHE_CLEANUP_MS)
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

    fun clearTrendsCache() {
        tmdbTrendsRepository.clearCache()
    }

    suspend fun getCategoryCache(providerName: String): Map<String, List<Movie>>? =
        homeCacheRepository.getCategoryCache(providerName)

    suspend fun saveCategoryCache(providerName: String, categories: Map<String, List<Movie>>) =
        homeCacheRepository.saveCategoryCache(providerName, categories)

    suspend fun isCategoryCacheStale(providerName: String, staleHours: Long = 6): Boolean =
        homeCacheRepository.isCategoryCacheStale(providerName, staleHours)

    fun getHomeGrid(provider: MediaProvider, forceRefresh: Boolean = false): Flow<List<Movie>> =
        homeSource.getHomeGrid(provider, forceRefresh)

    suspend fun getTmdbTrends(provider: MediaProvider, forceRefresh: Boolean = false): List<Movie> =
        tmdbTrendsRepository.getTrends(provider, forceRefresh)

    suspend fun getTmdbTrendsCached(provider: MediaProvider): List<Movie> =
        tmdbTrendsRepository.getCachedTrends(provider)

    private val parseSeasonEpisodeRegex = Regex("""(?:s|season)[^\d]*(\d+)[^\d]*(?:e|ep|episode)[^\d]*(\d+)""", RegexOption.IGNORE_CASE)

    private fun parseSeasonEpisode(episodeId: String?): Pair<Int?, Int?> {
        if (episodeId == null) return null to null
        val match = parseSeasonEpisodeRegex.find(episodeId) ?: return null to null
        return match.groupValues[1].toIntOrNull() to match.groupValues[2].toIntOrNull()
    }

    /**
     * Grouping key for "Продовжити перегляд". The same film can be stored under different
     * contentIds (Uakino vs UAFLIX, cross-provider playback fallback) with slightly different
     * title spellings, so a plain title string is not enough. We clean + normalize +
     * transliterate (same technique as the search/matching layer). Deliberately YEAR-FREE:
     * «Форсаж 10 (2023)» and «Форсаж 10» are the same film and must land in one group. Blank
     * titles fall back to the contentId so unrelated blank-title rows are never merged.
     */
    internal fun continueWatchingTitleKey(progress: ua.ukrtv.app.domain.model.WatchProgress): String {
        val cleaned = ua.ukrtv.app.matching.SearchScorer.transliterate(
            ua.ukrtv.app.matching.SearchScorer.normalizeTitle(
                ContentUtils.cleanTitleForDedupe(progress.title)
            )
        )
        return if (cleaned.isEmpty()) "id:${progress.contentId}" else cleaned
    }

    /**
     * Year extracted from the title itself (never from the page URL — those often contain
     * unrelated ids). Used to keep same-title films from different years as separate cards.
     */
    private fun continueWatchingYearKey(progress: ua.ukrtv.app.domain.model.WatchProgress): String =
        ua.ukrtv.app.matching.SearchScorer.extractYear(progress.title)?.toString() ?: ""

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getContinueWatching(): Flow<List<Movie>> = watchProgressRepository.getAllProgress()
        .mapLatest { allProgress ->
            allProgress
                .filter {
                    // Pre-resolved "next episode" stream-cache entries carry no title, poster,
                    // position, or duration. They exist only so the next episode starts instantly;
                    // they must never surface as a "Продовжити перегляд" card.
                    val isPreResolvePlaceholder = it.title.isEmpty() && it.poster.isEmpty() &&
                            it.positionMs <= 0L && it.durationMs <= 0L

                    // Keep if:
                    // 1. Progress is between 1% and 95%
                    // 2. OR it's a series episode (even at 0%, to show "Next episode")
                    // 3. OR the user actually started watching (positionMs > 0) — even a short
                    //    session must survive, because series opened without a parsed episode
                    //    structure are stored movie-level (episodeId == null) and would otherwise
                    //    vanish when the duration is unknown (progressPercentage == 0)
                    val isSeries = it.episodeId != null
                    val hasProgress = it.positionMs > 0L
                    !isPreResolvePlaceholder &&
                            it.progressPercentage < 96 && (it.progressPercentage > 0 || isSeries || hasProgress)
                }
                .fold(linkedMapOf<String, MutableMap<String, ua.ukrtv.app.domain.model.WatchProgress>>()) { groups, progress ->
                    val yearGroups = groups.getOrPut(continueWatchingTitleKey(progress)) { linkedMapOf() }
                    val yearKey = continueWatchingYearKey(progress)
                    val existing = yearGroups[yearKey]
                    if (existing == null || progressIsPreferred(progress, existing)) {
                        yearGroups[yearKey] = progress
                    }
                    groups
                }
                .flatMap { (_, yearGroups) ->
                    // All year keys known and distinct -> separate cards (King Kong 2005 vs 2017).
                    // Any unknown year in the group -> the same film, collapse to a single card.
                    if (yearGroups.keys.all { it.isNotEmpty() }) {
                        yearGroups.values
                    } else {
                        listOf(yearGroups.values.reduce { acc, progress ->
                            if (progressIsPreferred(progress, acc)) progress else acc
                        })
                    }
                }
                .mapNotNull { progress ->
                    val pUrl = progress.pageUrl
                    if (pUrl.isEmpty()) return@mapNotNull null
                    val (season, episode) = parseSeasonEpisode(progress.episodeId)

                    val providerName = when {
                        pUrl.contains("uaflix") || pUrl.contains("uafix") -> "UAFLIX"
                        else -> "Uakino"
                    }

                    Movie(
                        id = progress.contentId,
                        title = ContentUtils.cleanTitle(progress.title),
                        poster = progress.poster,
                        pageUrl = pUrl,
                        watchProgress = progress.progressPercentage,
                        contentType = if (season != null || progress.episodeId != null) "СЕРІАЛ" else null,
                        season = season,
                        episode = episode,
                        provider = providerName
                    )
                }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /**
     * When the same film appears under multiple entries, prefer the one the user actually has
     * in progress (has a poster and the most recent timestamp), never the stale pre-resolved
     * "next episode" placeholder.
     */
    private fun progressIsPreferred(
        candidate: ua.ukrtv.app.domain.model.WatchProgress,
        existing: ua.ukrtv.app.domain.model.WatchProgress
    ): Boolean {
        val candidateHasPoster = candidate.poster.isNotEmpty()
        val existingHasPoster = existing.poster.isNotEmpty()
        if (candidateHasPoster != existingHasPoster) return candidateHasPoster
        return candidate.timestamp > existing.timestamp
    }

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
