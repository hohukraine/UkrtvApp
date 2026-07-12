package ua.ukrtv.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.model.MediaLaunchState
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.data.TtlLruCache
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.repository.ContentRepository
import ua.ukrtv.app.data.repository.WatchProgressRepository
import ua.ukrtv.app.data.repository.WatchlistRepository
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.player.PlayerWarmupManager
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PerformanceMonitor
import ua.ukrtv.app.util.PerformancePreferences
import ua.ukrtv.app.util.PerformanceProfile
import javax.inject.Inject

sealed class DetailState {
    object Loading : DetailState()
    data class Success(
        val detail: MovieDetail,
        val watchProgress: Long = 0L
    ) : DetailState()
    data class Error(val message: String) : DetailState()
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mediaRepository: ContentRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val watchlistRepository: WatchlistRepository,
    private val streamResolver: StreamResolver,
    private val warmupManager: PlayerWarmupManager,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val providerManager: ProviderManager,
    private val performancePreferences: PerformancePreferences
) : ViewModel() {

    val performanceProfile: StateFlow<PerformanceProfile> = performancePreferences.profile

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state

    private val _launchState = MutableStateFlow<MediaLaunchState>(MediaLaunchState.Idle)
    val launchState: StateFlow<MediaLaunchState> = _launchState

    private val _isInWatchlist = MutableStateFlow(false)
    val isInWatchlist: StateFlow<Boolean> = _isInWatchlist

    private var currentDetail: MovieDetail? = null
    private var preWarmJob: Job? = null
    private val preWarmCache = TtlLruCache<String, StreamResolutionResult>(maxSize = 10, ttlMs = 5 * 60 * 1000L)

    init {
        val id = savedStateHandle.get<String>("id")
        val url = savedStateHandle.get<String>("url")
        val alternate = savedStateHandle.get<String>("alternate")
        AppLogger.d("DetailVM", "init id=$id url=${url?.take(40)} alternate=${alternate?.take(40)}")
        ua.ukrtv.app.util.Perf.start("detail:load")

        if (id != null && url != null) {
            loadDetail(id, url, alternate)
        } else {
            _state.value = DetailState.Error("Відсутні дані для завантаження")
        }
    }

    fun retry() {
        val id = savedStateHandle.get<String>("id")
        val url = savedStateHandle.get<String>("url")
        val alternate = savedStateHandle.get<String>("alternate")
        if (id != null && url != null) {
            loadDetail(id, url, alternate)
        }
    }

    private fun loadDetail(id: String, url: String, alternateUrl: String? = null) {
        viewModelScope.launch {
            PerformanceMonitor.begin("DetailVM.loadDetail")
            _state.value = DetailState.Loading
            val loadT = System.currentTimeMillis()
            
            val progress = watchProgressRepository.getProgress(id)

            mediaRepository.getDetails(id, url, alternateUrl).first().onSuccess { detail ->
                ua.ukrtv.app.util.Perf.end("detail:load", "DetailVM")
                AppLogger.d("DetailVM", "Detail loaded: ${detail.title} actors=${detail.actors.size} seasons=${detail.seasons?.size} in ${System.currentTimeMillis() - loadT}ms")
                currentDetail = detail
                _isInWatchlist.value = watchlistRepository.isInWatchlist(detail.id)
                _state.value = DetailState.Success(
                    detail = detail,
                    watchProgress = progress?.positionMs ?: 0L
                )

                if (detail.seasons.isNullOrEmpty()) {
                    viewModelScope.launch {
                        val enriched = mediaRepository.enrichSeasons(url, detail)
                        if (enriched.seasons.isNullOrEmpty().not()) {
                            currentDetail = enriched

                            val hasRealEpisodes = enriched.seasons.any { season ->
                                season.episodes.any { it.number > 1 }
                            }

                            if (hasRealEpisodes) {
                                _state.value = DetailState.Success(
                                    detail = enriched,
                                    watchProgress = progress?.positionMs ?: 0L
                                )
                            }
                            preWarmStream(enriched, progress?.episodeId?.let { parseSeasonEpisode(it) })
                        }
                    }
                } else {
                    preWarmStream(detail, progress?.episodeId?.let { parseSeasonEpisode(it) })
                }
            }.onFailure { e ->
                ua.ukrtv.app.util.Perf.end("detail:load", "DetailVM")
                AppLogger.d("DetailVM", "Detail load FAILED: ${e.message}")
                _state.value = DetailState.Error(e.message ?: "Не вдалося завантажити деталі")
            }
            PerformanceMonitor.end()
        }
    }

    private fun parseSeasonEpisode(episodeId: String): Pair<Int, Int>? {
        val regex = Regex("""s(\d+)e(\d+)""", RegexOption.IGNORE_CASE)
        val m = regex.find(episodeId) ?: return null
        val s = m.groupValues[1].toIntOrNull() ?: return null
        val e = m.groupValues[2].toIntOrNull() ?: return null
        return s to e
    }

    private fun preWarmStream(detail: MovieDetail, fromProgress: Pair<Int, Int>? = null) {
        preWarmJob?.cancel()
        val isSeries = !detail.seasons.isNullOrEmpty()
        val targetSeason = fromProgress?.first ?: if (isSeries) detail.seasons.firstOrNull()?.number ?: 1 else null
        val targetEpisode = fromProgress?.second ?: if (isSeries && targetSeason != null) {
            detail.seasons.find { it.number == targetSeason }?.episodes?.firstOrNull()?.number ?: 1
        } else null

        preWarmJob = viewModelScope.launch {
            try {
                AppLogger.d("DetailVM", "Pre-warming S${targetSeason}E${targetEpisode} for ${detail.title}")
                val result = streamResolver.resolve(
                    url = detail.pageUrl,
                    season = targetSeason,
                    episode = targetEpisode,
                    isDeep = !isSeries
                )
                if (result != null) {
                    val cacheKey = "${detail.pageUrl}|${targetSeason}|${targetEpisode}"
                    preWarmCache.put(cacheKey, result)
                } else {
                    val otherProvider = when {
                        detail.pageUrl.contains("uakino") -> providerManager.eneyidaProvider
                        detail.pageUrl.contains("eneyida") -> providerManager.uakinoProvider
                        else -> null
                    }
                    if (otherProvider != null) {
                        AppLogger.d("DetailVM", "Pre-warm primary failed, trying ${otherProvider.name} fallback for ${detail.title}")
                        val match = otherProvider.search(detail.title, limit = 5).firstOrNull()
                        if (match != null) {
                            val fallbackResult = streamResolver.resolve(
                                url = match.url,
                                season = targetSeason,
                                episode = targetEpisode,
                                isDeep = !isSeries
                            )
                            if (fallbackResult != null) {
                                val cacheKey = "${match.url}|${targetSeason}|${targetEpisode}"
                                preWarmCache.put(cacheKey, fallbackResult)
                            }
                        }
                    }
                }
                AppLogger.d("DetailVM", "Pre-warm complete for ${detail.title}")
            } catch (_: Exception) { }
        }
    }

    fun watchContent(season: Int? = null, episode: Int? = null, voiceover: String? = null) {
        val detail = currentDetail ?: return
        _launchState.value = MediaLaunchState.Resolving(detail.title)

        viewModelScope.launch {
            try {
                val cacheKey = "${detail.pageUrl}|${season}|${episode}"
                val cachedRes = preWarmCache.get(cacheKey)

                val res = cachedRes ?: withContext(Dispatchers.IO) {
                    streamResolver.resolve(
                        url = detail.pageUrl,
                        referer = "",
                        season = season,
                        episode = episode,
                        voiceover = voiceover
                    )
                }

                if (res != null) {
                    val dsFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
                        .setUserAgent(ua.ukrtv.app.Constants.USER_AGENT)
                    warmupManager.warmup(url = res.streamUrl, dataSourceFactory = dsFactory)
                    _launchState.value = MediaLaunchState.Ready(
                        contentId = detail.id,
                        title = detail.title,
                        subtitle = if (season != null && episode != null) "S$season E$episode" else "",
                        posterUrl = detail.poster,
                        streamResult = res,
                        season = season,
                        episode = episode,
                        voiceover = voiceover,
                        seasons = res.seasons ?: detail.seasons
                    )
                } else {
                    val otherProvider = when {
                        detail.pageUrl.contains("uakino") -> providerManager.eneyidaProvider
                        detail.pageUrl.contains("eneyida") -> providerManager.uakinoProvider
                        else -> null
                    }
                    if (otherProvider != null) {
                        AppLogger.d("DetailViewModel", "Primary failed, trying ${otherProvider.name} fallback for ${detail.title}")
                        try {
                            val results = otherProvider.search(detail.title, limit = 5)
                            val match = results.firstOrNull()
                            if (match != null) {
                                val res2 = withContext(Dispatchers.IO) {
                                    streamResolver.resolve(
                                        url = match.url,
                                        referer = "",
                                        season = season,
                                        episode = episode,
                                        voiceover = voiceover
                                    )
                                }
                                if (res2 != null) {
                                    val dsFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
                                        .setUserAgent(ua.ukrtv.app.Constants.USER_AGENT)
                                    warmupManager.warmup(url = res2.streamUrl, dataSourceFactory = dsFactory)
                                    _launchState.value = MediaLaunchState.Ready(
                                        contentId = detail.id,
                                        title = detail.title,
                                        subtitle = if (season != null && episode != null) "S$season E$episode" else "",
                                        posterUrl = detail.poster,
                                        streamResult = res2,
                                        season = season,
                                        episode = episode,
                                        voiceover = voiceover,
                                        seasons = res2.seasons ?: detail.seasons
                                    )
                                    return@launch
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLogger.e("DetailViewModel", "${otherProvider.name} fallback error for ${detail.title}", e)
                        }
                    }
                    _launchState.value = MediaLaunchState.Error("Стрім не знайдено")
                    AppLogger.e("DetailViewModel", "All providers failed for ${detail.title}")
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _launchState.value = MediaLaunchState.Error("Час очікування вичерпано")
                AppLogger.e("DetailViewModel", "Stream resolve timeout for ${detail.title}", e)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _launchState.value = MediaLaunchState.Error(e.message ?: "Помилка розв'язання стріму")
                    AppLogger.e("DetailViewModel", "Stream resolve failed for ${detail.title}", e)
                }
            }
        }
    }

    fun resetLaunchState() {
        _launchState.value = MediaLaunchState.Idle
    }

    fun toggleWatchlist() {
        val detail = currentDetail ?: return
        viewModelScope.launch {
            if (_isInWatchlist.value) {
                watchlistRepository.removeFromWatchlist(detail.id)
                _isInWatchlist.value = false
            } else {
                watchlistRepository.addToWatchlist(
                    Movie(
                        id = detail.id,
                        title = detail.title,
                        poster = detail.poster,
                        pageUrl = detail.pageUrl,
                        rating = detail.rating,
                        year = detail.year?.toIntOrNull(),
                        contentType = if (detail.seasons == null) "movie" else "series"
                    )
                )
                _isInWatchlist.value = true
            }
        }
    }
}
