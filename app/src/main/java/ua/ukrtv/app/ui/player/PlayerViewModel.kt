package ua.ukrtv.app.ui.player

import android.content.Context
import android.os.Build
import ua.ukrtv.app.util.AppLogger
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.ukrtv.app.data.repository.WatchProgressRepository
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.domain.model.MediaLaunchState
import ua.ukrtv.app.player.PlayerFactory
import ua.ukrtv.app.player.PlaybackErrorHandler
import ua.ukrtv.app.player.PlayerWarmupManager
import ua.ukrtv.app.player.ThermalMonitor
import ua.ukrtv.app.util.PerformanceMonitor
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
    private val watchProgressRepository: WatchProgressRepository,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val streamResolver: StreamResolver,
    private val playerFactory: PlayerFactory,
    private val thermalMonitor: ThermalMonitor,
    private val warmupManager: PlayerWarmupManager
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            thermalMonitor.thermalStatus.collect { status ->
                val quality = thermalMonitor.getQualityLevel(status)
                AppLogger.d("ThermalMonitor", "Quality level: $quality (status=$status)")
                applyThermalQuality(quality)
                _state.update { it.copy(thermalStatus = status) }
            }
        }
        viewModelScope.launch {
            savedStateHandle.getStateFlow<SeasonSelectionResult?>("episode_result", null).collect { result ->
                if (result != null) {
                    onEpisodeSelected(result.season, result.episode, result.voiceover)
                    savedStateHandle.remove<SeasonSelectionResult>("episode_result")
                }
            }
        }
    }

    private var context = PlayerContext()
    private var loadJob: kotlinx.coroutines.Job? = null
    private var isResolving = false
    var player: ExoPlayer? = null
        private set

    val trackManager = TrackManager()

    init {
        val launchState = savedStateHandle.get<MediaLaunchState.Ready>("launch_state")
        if (launchState != null) {
            val res = launchState.streamResult
            context = PlayerContext(
                contentId = launchState.contentId,
                title = launchState.title,
                pageUrl = res.sourcePageUrl,
                poster = launchState.posterUrl,
                season = launchState.season,
                episode = launchState.episode,
                episodeId = if (launchState.season != null && launchState.episode != null) "s${launchState.season}e${launchState.episode}" else null,
                voiceover = launchState.voiceover,
                referer = res.referer,
                subtitle = if (launchState.season != null && launchState.episode != null) "S${launchState.season} E${launchState.episode}" else "",
                seasons = launchState.seasons ?: res.seasons
            )
            _state.update { it.copy(
                status = PlayerStatus.Ready(res.streamUrl, context.title, context.subtitle, 0L, res.referer, res.streamType),
                availableSeasons = context.seasons
            ) }
        }

        viewModelScope.launch {
            thermalMonitor.thermalStatus.collect { status ->
                val quality = thermalMonitor.getQualityLevel(status)
                AppLogger.d("ThermalMonitor", "Quality level: $quality (status=$status)")
                applyThermalQuality(quality)
                _state.update { it.copy(thermalStatus = status) }
            }
        }
    }

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.Initialize -> initialize(intent.contentId, intent.title, intent.pageUrl, intent.season, intent.episode, intent.poster)
            is PlayerIntent.TogglePlay -> togglePlay()
            is PlayerIntent.ToggleMute -> toggleMute()
            is PlayerIntent.ToggleControls -> setShowControls(!_state.value.showControls)
            is PlayerIntent.ToggleStats -> _state.update { 
                it.copy(showStats = !it.showStats)
            }
            is PlayerIntent.ToggleChildMode -> _state.update { it.copy(childMode = !it.childMode) }
            is PlayerIntent.Retry -> retry()
            is PlayerIntent.SeekTo -> player?.seekTo(intent.positionMs)
            is PlayerIntent.ChangeResizeMode -> _state.update { it.copy(videoResizeMode = intent.mode) }
            is PlayerIntent.SetShowControls -> setShowControls(intent.show)
            is PlayerIntent.NavigateNext -> navigateToNextEpisode()
            is PlayerIntent.NavigatePrevious -> navigateToPreviousEpisode()
            is PlayerIntent.SelectEpisode -> onEpisodeSelected(intent.season, intent.episode, intent.voiceover)
            is PlayerIntent.UpdateProgress -> _state.update { it.copy(currentPosition = intent.positionMs, duration = intent.durationMs) }
            is PlayerIntent.UpdatePlaybackState -> _state.update { it.copy(playbackState = intent.state) }
            is PlayerIntent.UpdateIsPlaying -> _state.update { it.copy(isPlaying = intent.isPlaying) }
        }
    }

    private fun initialize(contentId: String, title: String, pageUrl: String, season: Int? = null, episode: Int? = null, poster: String = "") {
        if (_state.value.status is PlayerStatus.Ready || _state.value.status is PlayerStatus.Loading) {
            if (context.contentId == contentId && context.season == season && context.episode == episode) return
        }
        if (isResolving && context.contentId == contentId && context.season == season && context.episode == episode) return

        val voiceover = savedStateHandle.get<MediaLaunchState.Ready>("launch_state")?.voiceover
        context = PlayerContext(
            contentId = contentId, title = title, pageUrl = pageUrl, poster = poster,
            season = season, episode = episode,
            episodeId = if (season != null && episode != null) "s${season}e${episode}" else null,
            voiceover = voiceover
        )

        updateNavigationState()

        val preResolved = savedStateHandle.get<MediaLaunchState.Ready>("launch_state")
        if (preResolved != null && preResolved.contentId == contentId && preResolved.season == season && preResolved.episode == episode) {
            val res = preResolved.streamResult
            context.referer = res.referer
            context.subtitle = if (season != null && episode != null) "S$season E$episode" else ""
            context.seasons = preResolved.seasons ?: res.seasons
            
            _state.update { it.copy(
                status = PlayerStatus.Ready(res.streamUrl, context.title, context.subtitle, 0L, res.referer, res.streamType),
                availableSeasons = context.seasons
            ) }
        } else {
            loadStream(pageUrl, if (season != null && episode != null) "S$season E$episode" else "")
        }
    }

    private fun loadStream(url: String, subtitle: String) {
        PerformanceMonitor.begin("PlayerVM.loadStream")
        loadJob?.cancel()
        isResolving = true
        _state.update { it.copy(status = PlayerStatus.Loading(context.title)) }
        loadJob = viewModelScope.launch {
            try {
                val res = streamResolver.resolve(url, season = context.season, episode = context.episode, voiceover = context.voiceover, isDeep = false)
                if (res != null) {
                    context.availableStreams = (listOf(res.streamUrl) + res.fallbackStreams).distinct().toMutableList()
                    context.referer = res.referer
                    context.seasons = res.seasons
                    val pos = getSavedPosition()
                    _state.update { it.copy(
                        status = PlayerStatus.Ready(res.streamUrl, context.title, subtitle, pos, res.referer, res.streamType),
                        availableSeasons = context.seasons
                    ) }
                    updateNavigationState()
                    launchDeepResolution()
                } else {
                    _state.update { it.copy(status = PlayerStatus.Error(appContext.getString(ua.ukrtv.app.R.string.video_not_found))) }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _state.update { it.copy(status = PlayerStatus.Error(e.message ?: "Unknown Error")) }
                }
            } finally {
                isResolving = false
                PerformanceMonitor.end()
            }
        }
    }

    private fun launchDeepResolution() {
        viewModelScope.launch {
            val deepRes = try { streamResolver.resolve(context.pageUrl, isDeep = true) } catch (_: Exception) { null }
            if (deepRes?.seasons != null) {
                context.seasons = deepRes.seasons
                _state.update { it.copy(availableSeasons = deepRes.seasons) }
                updateNavigationState()
            }
        }
    }

    private fun updateNavigationState() {
        _state.update { it.copy(
            currentSeason = context.season,
            currentEpisode = context.episode,
            currentVoiceover = context.voiceover,
            availableSeasons = context.seasons
        ) }
    }

    private suspend fun getSavedPosition(): Long = withContext(Dispatchers.IO) {
        watchProgressRepository.getProgress(context.contentId, context.episodeId)?.positionMs ?: 0L
    }

    private var prefetchJob: kotlinx.coroutines.Job? = null

    fun saveProgress(pos: Long, dur: Long) {
        if (dur <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            watchProgressRepository.saveProgress(context.contentId, context.episodeId, pos, dur, context.title, context.poster, context.pageUrl)
        }
        prefetchNextEpisodeIfNeeded(pos, dur)
    }

    private fun prefetchNextEpisodeIfNeeded(pos: Long, dur: Long) {
        if (prefetchJob?.isActive == true) return
        if (dur <= 0 || pos.toFloat() / dur < 0.85f) return
        val nav = EpisodeNavigator.nextEpisode(context) ?: return
        prefetchJob = viewModelScope.launch {
            try {
                val res = streamResolver.resolve(
                    url = context.pageUrl,
                    season = nav.season,
                    episode = nav.episode,
                    voiceover = context.voiceover,
                    isDeep = false
                )
                if (res != null) {
                    val dsFactory = getDataSourceFactory()
                    warmupManager.warmup(url = res.streamUrl, dataSourceFactory = dsFactory)
                }
            } catch (_: Exception) {}
        }
    }

    private fun togglePlay() { player?.let { if (it.isPlaying) it.pause() else it.play() } }
    private fun toggleMute() {
        val newMuted = !_state.value.isMuted
        _state.update { it.copy(isMuted = newMuted) }
        player?.volume = if (newMuted) 0f else 1f
    }
    private var hideJob: kotlinx.coroutines.Job? = null

    private fun setShowControls(show: Boolean) {
        _state.update { it.copy(showControls = show) }
        hideJob?.cancel()
    }
    private fun retry() { loadStream(context.pageUrl, context.subtitle) }

    fun onPlayerError(error: PlaybackException) {
        if (PlaybackErrorHandler.shouldFallbackStream(error)) {
            if (context.currentStreamIndex < context.availableStreams.size - 1) {
                context.currentStreamIndex++
                val fallbackUrl = context.availableStreams[context.currentStreamIndex]
                val currentStatus = _state.value.status
                if (currentStatus is PlayerStatus.Ready) {
                    _state.update { it.copy(status = currentStatus.copy(url = fallbackUrl, loadTrigger = System.currentTimeMillis())) }
                    return
                }
            }
        }
        if (PlaybackErrorHandler.shouldRetry(error) && context.retryCount < 2) {
            context.retryCount++
            loadStream(context.pageUrl, context.subtitle)
            return
        }
        _state.update { it.copy(status = PlayerStatus.Error(PlaybackErrorHandler.getUserMessage(error))) }
    }

    fun prepareNextEpisode(): Boolean {
        if (_state.value.childMode) return false
        val nav = EpisodeNavigator.nextEpisode(context) ?: return false
        context.season = nav.season
        context.episode = nav.episode
        context.episodeId = "s${nav.season}e${nav.episode}"
        return true
    }

    fun executePreparedNavigation() {
        loadStream(context.pageUrl, "S${context.season} E${context.episode}")
        updateNavigationState()
    }

    private fun navigateToNextEpisode(): Boolean {
        if (_state.value.childMode) return false
        val nav = EpisodeNavigator.nextEpisode(context) ?: return false
        applyEpisodeNavigation(nav.season, nav.episode)
        return true
    }

    private fun navigateToPreviousEpisode(): Boolean {
        val nav = EpisodeNavigator.previousEpisode(context) ?: return false
        applyEpisodeNavigation(nav.season, nav.episode)
        return true
    }

    private fun applyEpisodeNavigation(season: Int, episode: Int) {
        context.season = season
        context.episode = episode
        context.episodeId = "s${season}e${episode}"
        loadStream(context.pageUrl, "S$season E$episode")
        updateNavigationState()
    }

    private fun onEpisodeSelected(s: Int, e: Int, voiceover: String?) {
        context.voiceover = voiceover ?: context.voiceover
        applyEpisodeNavigation(s, e)
    }

    fun hasNextEpisode(): Boolean = EpisodeNavigator.hasNextEpisode(context)
    fun hasPreviousEpisode(): Boolean = EpisodeNavigator.hasPreviousEpisode(context)

    fun releasePlayer(playerToRelease: ExoPlayer) {
        if (player === playerToRelease) player = null
        playerToRelease.release()
    }

    fun getOrCreatePlayer(context: Context, dsFactory: DataSource.Factory): ExoPlayer {
        if (player == null) {
            val warmedUp = warmupManager.takeAnyWarmupPlayer()
            if (warmedUp != null) {
                player = warmedUp
                AppLogger.d("Warmup", "Using warmup player")
            } else {
                AppLogger.d("Warmup", "No warmup available, creating new player")
                player = playerFactory.buildPlayer(context, dsFactory)
            }
        }
        return player!!
    }

    private fun applyThermalQuality(quality: ThermalMonitor.QualityLevel) {
        val exoPlayer = player ?: return
        val trackSelector = try {
            (exoPlayer.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector)
        } catch (_: Exception) { null } ?: return

        val params = when (quality) {
            ThermalMonitor.QualityLevel.HIGH -> trackSelector.buildUponParameters()
                .setMaxVideoSize(1920, 1080)
                .setPreferredAudioLanguage("ukr")
            ThermalMonitor.QualityLevel.MEDIUM -> trackSelector.buildUponParameters()
                .setMaxVideoSize(1280, 720)
                .setPreferredAudioLanguage("ukr")
            ThermalMonitor.QualityLevel.LOW -> trackSelector.buildUponParameters()
                .setMaxVideoSize(854, 480)
                .setPreferredAudioLanguage("ukr")
            ThermalMonitor.QualityLevel.MINIMAL -> trackSelector.buildUponParameters()
                .setMaxVideoSize(640, 360)
                .setPreferredAudioLanguage("ukr")
        }
        trackSelector.setParameters(params.build())
    }

    fun getDataSourceFactory(): DataSource.Factory {
        return OkHttpDataSource.Factory(okHttpClient).setUserAgent(ua.ukrtv.app.Constants.USER_AGENT)
    }

    override fun onCleared() {
        super.onCleared()
        player?.let {
            it.stop()
            it.release()
        }
        player = null
        loadJob?.cancel()
        prefetchJob?.cancel()
        hideJob?.cancel()
    }
}
