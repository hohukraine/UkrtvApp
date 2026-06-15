package ua.ukrtv.app.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.data.repository.WatchProgressRepository
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.domain.model.WatchProgress
import ua.ukrtv.app.domain.usecase.GetMovieDetailsUseCase
import ua.ukrtv.app.player.PlaybackErrorHandler
import ua.ukrtv.app.player.PlayerFactory
import ua.ukrtv.app.util.PlaybackStatsTracker
import javax.inject.Inject

sealed class PlayerUiState {
    object Idle : PlayerUiState()
    data class Loading(val title: String) : PlayerUiState()
    data class Ready(
        val url: String, 
        val title: String, 
        val subtitle: String, 
        val positionMs: Long, 
        val referer: String, 
        val streamType: StreamType,
        val loadTrigger: Long = System.currentTimeMillis() // To force re-load if URL is same but we need re-prepare
    ) : PlayerUiState()
    data class Error(val message: String, val category: PlaybackErrorHandler.ErrorCategory, val isRetryable: Boolean = true) : PlayerUiState()
    data class SeriesSelection(val seasons: List<Season>) : PlayerUiState()
}

sealed class PlayerEvent {
    data class ShowToast(val message: String) : PlayerEvent()
    object Finish : PlayerEvent()
}

data class SyncEvent(val remote: WatchProgress, val local: WatchProgress)

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val watchProgressRepository: WatchProgressRepository,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val streamResolver: StreamResolver,
    private val playerFactory: PlayerFactory,
    private val playbackStatsTracker: PlaybackStatsTracker,
    private val getMovieDetailsUseCase: GetMovieDetailsUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val uiState: StateFlow<PlayerUiState> = _uiState

    private val _events = MutableSharedFlow<PlayerEvent>()
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private val _syncEvent = MutableStateFlow<SyncEvent?>(null)
    val syncEvent: StateFlow<SyncEvent?> = _syncEvent

    private val _showControls = MutableStateFlow(true)
    val showControls: StateFlow<Boolean> = _showControls

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _nextEpisodeCountdown = MutableStateFlow<Int?>(null)
    val nextEpisodeCountdown: StateFlow<Int?> = _nextEpisodeCountdown

    private val _decoderInfo = MutableStateFlow<String?>(null)
    val decoderInfo: StateFlow<String?> = _decoderInfo

    // Internal state (FSM Context)
    private var context = PlayerContext()

    data class PlayerContext(
        var contentId: String = "",
        var title: String = "",
        var subtitle: String = "",
        var uakinoUrl: String = "",
        var pageUrl: String = "",
        var poster: String = "",
        var referer: String = "",
        var season: Int? = null,
        var episode: Int? = null,
        var episodeId: String? = null,
        var seasons: List<Season>? = null,
        var availableStreams: MutableList<String> = mutableListOf(),
        var currentStreamIndex: Int = 0,
        var retryCount: Int = 0
    )

    private var lastDecoderName: String? = null
    private var lastFormat: Format? = null

    fun initialize(
        contentId: String,
        title: String,
        uakinoUrl: String,
        hlsUrl: String = "",
        season: Int? = null,
        episode: Int? = null,
        playbackResult: StreamResolutionResult? = null,
        seasons: List<Season>? = null,
        referer: String = "",
        episodeTitle: String = "",
        poster: String = "",
        pageUrl: String = ""
    ) {
        context = PlayerContext(
            contentId = contentId,
            title = title,
            uakinoUrl = uakinoUrl,
            pageUrl = pageUrl,
            poster = poster,
            referer = referer,
            season = season,
            episode = episode,
            episodeId = if (season != null && episode != null && season != -1 && episode != -1) "s${season}e${episode}" else null,
            seasons = seasons,
            subtitle = episodeTitle
        )

        if (seasons != null) {
            handleInitialSelection(seasons, season, episode, referer)
        } else if (contentId.isNotEmpty() && (uakinoUrl.isNotEmpty() || pageUrl.isNotEmpty())) {
            fetchDetails(contentId, uakinoUrl.ifEmpty { pageUrl }, season, episode)
        } else if (playbackResult != null) {
            loadStream(playbackResult.streamUrl, title, playbackResult.referer.ifEmpty { referer })
        } else if (hlsUrl.isNotEmpty()) {
            loadStream(hlsUrl, episodeTitle, referer)
        } else {
            _uiState.value = PlayerUiState.Error("Відсутні дані для відтворення", PlaybackErrorHandler.ErrorCategory.UNKNOWN, false)
        }
    }

    private fun handleInitialSelection(seasons: List<Season>, s: Int?, e: Int?, referer: String) {
        if (s != null && e != null && s != -1 && e != -1) {
            val ep = seasons.find { it.number == s }
                ?.episodes?.find { it.number == e }
            val url = ep?.pageUrl
            if (!url.isNullOrEmpty()) {
                loadStream(url, ep.title, referer)
            } else {
                _uiState.value = PlayerUiState.SeriesSelection(seasons)
            }
        } else {
            _uiState.value = PlayerUiState.SeriesSelection(seasons)
        }
    }

    private fun fetchDetails(id: String, url: String, s: Int?, e: Int?) {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading(context.title)
            getMovieDetailsUseCase(id, url).collect { result ->
                result.onSuccess { detail ->
                    context.seasons = detail.seasons
                    context.pageUrl = detail.pageUrl
                    
                    // Перевірка на "скелет" сезонів від TMDB (без серій)
                    val hasEpisodes = detail.seasons?.any { it.episodes.isNotEmpty() } == true
                    
                    if (detail.contentType == ContentType.SERIES && !hasEpisodes) {
                        fetchEpisodesFromProvider(detail.pageUrl, s, e)
                    } else if (!detail.seasons.isNullOrEmpty()) {
                        handleInitialSelection(detail.seasons, s, e, "")
                    } else {
                        loadStream(url, context.title)
                    }
                }.onFailure {
                    _uiState.value = PlayerUiState.Error("Помилка завантаження даних серіалу", PlaybackErrorHandler.ErrorCategory.UNKNOWN)
                }
            }
        }
    }

    private fun fetchEpisodesFromProvider(pageUrl: String, targetSeason: Int?, targetEpisode: Int?) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                streamResolver.resolvePage(pageUrl)
            }
            val source = result?.source
            if (source is ua.ukrtv.app.data.providers.MediaSource.Series) {
                val domainSeasons = source.seasons.map { ps ->
                    Season(ps.number, ps.episodes.map { pe -> ua.ukrtv.app.domain.model.Episode(pe.url, pe.number, pe.title, pe.url) })
                }
                context.seasons = domainSeasons
                handleInitialSelection(domainSeasons, targetSeason, targetEpisode, result.referer)
            } else {
                _uiState.value = PlayerUiState.Error("Не вдалося знайти серії", PlaybackErrorHandler.ErrorCategory.UNKNOWN)
            }
        }
    }

    fun onEpisodeSelected(season: Int, episode: Int) {
        context.season = season
        context.episode = episode
        context.episodeId = "s${season}e${episode}"
        
        val ep = context.seasons?.find { it.number == season }
            ?.episodes?.find { it.number == episode }
        
        if (ep != null) {
            loadStream(ep.pageUrl, ep.title)
        }
    }

    fun setShowControls(show: Boolean) {
        _showControls.value = show
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun onPlaybackEnded() {
        // Logic for next episode can be added here
    }

    fun onPlayerError(error: PlaybackException) {
        val category = PlaybackErrorHandler.getErrorCategory(error)
        val userMsg = PlaybackErrorHandler.getUserMessage(error)

        // 1. Fallback to another stream quality/url
        if (PlaybackErrorHandler.shouldFallbackStream(error)) {
            if (context.currentStreamIndex < context.availableStreams.size - 1) {
                context.currentStreamIndex++
                val fallbackUrl = context.availableStreams[context.currentStreamIndex]
                val currentState = _uiState.value
                if (currentState is PlayerUiState.Ready) {
                    _uiState.value = currentState.copy(url = fallbackUrl, loadTrigger = System.currentTimeMillis())
                    return
                }
            }
        }

        // 2. Retry with same URL or reload provider
        if (PlaybackErrorHandler.shouldRetry(error) || PlaybackErrorHandler.isBlockedStream(error)) {
            if (context.retryCount < 2) {
                context.retryCount++
                val reloadUrl = context.uakinoUrl.ifEmpty { context.pageUrl }
                if (reloadUrl.isNotEmpty()) {
                    loadStream(reloadUrl, context.subtitle)
                    return
                }
            }
        }

        // 3. Unrecoverable error
        _uiState.value = PlayerUiState.Error(userMsg, category, isRetryable = true)
    }

    fun retryWithNextProvider() {
        val url = context.uakinoUrl.ifEmpty { context.pageUrl }
        if (url.isNotEmpty()) {
            context.retryCount = 0 // Reset retry count for manual retry
            loadStream(url, context.subtitle)
        }
    }

    fun loadStream(url: String, episodeTitle: String, referer: String? = null) {
        context.subtitle = episodeTitle
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading(context.title)
            val resolution = withContext(Dispatchers.IO) {
                streamResolver.resolve(url, referer ?: context.referer)
            }

            if (resolution != null) {
                context.availableStreams.clear()
                val primaryUrl = resolution.streamUrl
                if (primaryUrl.contains(".m3u8") || primaryUrl.contains(".mpd") || primaryUrl.contains(".mp4")) {
                    context.availableStreams.add(primaryUrl)
                }
                context.availableStreams.addAll(resolution.fallbackStreams)
                context.availableStreams = context.availableStreams.distinct().toMutableList()
                context.currentStreamIndex = 0

                val position = getSavedPosition()
                _uiState.value = PlayerUiState.Ready(
                    url = resolution.streamUrl,
                    title = context.title,
                    subtitle = episodeTitle,
                    positionMs = position,
                    referer = resolution.referer,
                    streamType = resolution.streamType
                )
                
                checkSyncProgress()
            } else {
                _uiState.value = PlayerUiState.Error("Стрім не знайдено", PlaybackErrorHandler.ErrorCategory.UNKNOWN)
            }
        }
    }

    private suspend fun getSavedPosition(): Long {
        return watchProgressRepository.getProgress(context.contentId, context.episodeId)?.positionMs ?: 0L
    }

    private fun checkSyncProgress() {
        viewModelScope.launch {
            val remote = watchProgressRepository.getProgressWithDeviceInfo(context.contentId, context.episodeId)
            val local = watchProgressRepository.getProgress(context.contentId, context.episodeId)
            
            if (remote != null && local != null) {
                if (remote.positionMs != local.positionMs && remote.timestamp > local.timestamp) {
                    if (kotlin.math.abs(remote.positionMs - local.positionMs) > 3000) {
                        _syncEvent.value = SyncEvent(remote, local)
                    }
                }
            }
        }
    }

    fun updateDecoderInfo(decoderName: String?, format: Format?, videoSize: VideoSize?) {
        if (decoderName != null) lastDecoderName = decoderName
        if (format != null) lastFormat = format
        
        val name = lastDecoderName ?: "Unknown"
        val f = lastFormat
        val width = videoSize?.width ?: f?.width ?: 0
        val height = videoSize?.height ?: f?.height ?: 0

        if (width > 0) {
            _decoderInfo.value = "Decoder: $name (${width}x${height})"
        }
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            watchProgressRepository.saveProgress(
                contentId = context.contentId,
                episodeId = context.episodeId,
                positionMs = positionMs,
                durationMs = durationMs,
                title = context.title,
                poster = context.poster,
                pageUrl = context.pageUrl
            )
        }
    }

    fun startTracking(player: ExoPlayer) {
        viewModelScope.launch {
            val deviceId = watchProgressRepository.getDeviceId()
            playbackStatsTracker.startTracking(player, deviceId, context.contentId, context.episodeId)
        }
    }

    fun stopTracking() {
        playbackStatsTracker.stopTracking()
    }

    fun dismissSyncDialog() {
        _syncEvent.value = null
    }

    fun getDataSourceFactory(): OkHttpDataSource.Factory {
        val playerOkHttpClient = okHttpClient.newBuilder()
            .connectTimeout(ua.ukrtv.app.Constants.CONNECT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(ua.ukrtv.app.Constants.READ_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val refererToUse = when {
                    context.referer.isNotEmpty() -> context.referer
                    url.contains("ashdi") || url.contains("hdvb") -> "https://uakino.best/"
                    url.contains("eneyida") -> "https://eneyida.tv/"
                    else -> "https://uakino.best/"
                }
                val originToUse = try {
                    val uri = java.net.URI(refererToUse)
                    "${uri.scheme}://${uri.host}"
                } catch (e: Exception) { refererToUse.substringBefore("/", refererToUse) }

                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", ua.ukrtv.app.Constants.USER_AGENT)
                        .header("Referer", refererToUse)
                        .header("Origin", originToUse)
                        .build()
                )
            }
            .build()
        return OkHttpDataSource.Factory(playerOkHttpClient)
    }

    fun buildPlayer(ctx: android.content.Context, dataSourceFactory: OkHttpDataSource.Factory): ExoPlayer {
        return playerFactory.buildPlayer(ctx, dataSourceFactory)
    }
}
