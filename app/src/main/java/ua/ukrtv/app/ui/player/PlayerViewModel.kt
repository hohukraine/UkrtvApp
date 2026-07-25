package ua.ukrtv.app.ui.player

import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.repository.WatchProgressRepository
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.player.AudioEngine
import ua.ukrtv.app.player.PlayerFactory
import ua.ukrtv.app.player.PlaybackEngine
import ua.ukrtv.app.player.PlaybackErrorHandler
import ua.ukrtv.app.player.ExoPlayerEngine
import ua.ukrtv.app.player.ExternalPlayerInfo
import ua.ukrtv.app.player.ExternalPlayerLauncher
import ua.ukrtv.app.player.MediaPrefetcher
import ua.ukrtv.app.player.ProviderQualityManager
import ua.ukrtv.app.player.ThermalMonitor

import ua.ukrtv.app.util.PerformanceMonitor
import ua.ukrtv.app.util.PlayerPreferences
import ua.ukrtv.app.util.PlayerType
import ua.ukrtv.app.domain.model.StreamType
import android.content.Intent
import javax.inject.Inject

@androidx.media3.common.util.UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext val appContext: Context,
    val savedStateHandle: SavedStateHandle,
    private val watchProgressRepository: WatchProgressRepository,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val streamResolver: StreamResolver,
    private val playerFactory: PlayerFactory,
    private val audioEngine: AudioEngine,
    private val providerManager: ProviderManager,
    val playerPreferences: PlayerPreferences,
    private val mediaPrefetcher: MediaPrefetcher,
    private val providerQualityManager: ProviderQualityManager,
    private val thermalMonitor: ThermalMonitor,
    private val streamResolvingInteractor: StreamResolvingInteractor,
    internal val externalPlayerInteractor: ExternalPlayerInteractor
) : ViewModel() {

    private val episodePickerManager = EpisodePickerManager(audioEngine)
    private val playbackManager = PlaybackManager(
        appContext, playerFactory, audioEngine, viewModelScope,
        onPlayerError = { onPlayerError(it) },
        onPlaybackStateChanged = { updatePlaybackState(it) },
        onIsPlayingChanged = { updateIsPlaying(it) },
        onPositionChanged = { /* Only for UI updates, handled by Viewport */ }
    )

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _deepResolutionCompleted = MutableStateFlow(false)
    val deepResolutionCompleted: StateFlow<Boolean> = _deepResolutionCompleted.asStateFlow()

    // Player Context Fields (Inlined)
    private var contentId: String = ""
    private var title: String = ""
    private var pageUrl: String = ""
    private var poster: String = ""
    private var referer: String = ""
    private var subtitle: String = ""
    private var season: Int? = null
    private var episode: Int? = null
    private var episodeId: String? = null
    private var voiceover: String? = null
    private var retryCount: Int = 0
    private var crossProviderRetried = false
    private var availableStreams: MutableList<String> = mutableListOf()
    private var currentStreamIndex: Int = 0
    private var seasons: List<Season> = emptyList()
        set(value) {
            field = value
        }

    private var loadJob: kotlinx.coroutines.Job? = null
    private var deepJob: kotlinx.coroutines.Job? = null
    private var preResolveJob: kotlinx.coroutines.Job? = null
    private var isResolving = false
    private var isAutoAdvancing = false
    private var savedBackgroundPosition: Long = 0L
    private var activeHttpFactory: OkHttpDataSource.Factory? = null

    private val externalPlayerLaunchLock = Any()
    private var externalPlayerLaunchLocked = false
    private var externalPlayerLaunchTimeMs = 0L

    fun tryAcquireExternalPlayerLaunchLock(): Boolean {
        synchronized(externalPlayerLaunchLock) {
            if (externalPlayerLaunchLocked) return false
            externalPlayerLaunchLocked = true
            return true
        }
    }

    var player: ExoPlayer?
        get() = playbackManager.player
        private set(_) {}

    private var engine: PlaybackEngine?
        get() = playbackManager.engine
        private set(_) {}

    val currentEngine: PlaybackEngine? get() = engine

    private var pendingSeason: Int?
        get() = episodePickerManager.pendingSeason
        set(v) { episodePickerManager.pendingSeason = v }
    private var pendingEpisode: Int?
        get() = episodePickerManager.pendingEpisode
        set(v) { episodePickerManager.pendingEpisode = v }
    private var pendingVoiceover: String?
        get() = episodePickerManager.pendingVoiceover
        set(v) { episodePickerManager.pendingVoiceover = v }
    private var pendingTrackIndex: Int?
        get() = episodePickerManager.pendingTrackIndex
        set(v) { episodePickerManager.pendingTrackIndex = v }
    private var selectedCodecMime: String? = null
    private var preparedSeason: Int? = null
    private var preparedEpisode: Int? = null

    val trackManager = TrackManager()

    val playerType: StateFlow<ua.ukrtv.app.util.PlayerType> = playerPreferences.playerType

    companion object {
        private const val KEY_CONTENT_ID = "ext_content_id"
        private const val KEY_PAGE_URL = "ext_page_url"
        private const val KEY_SEASON = "ext_season"
        private const val KEY_EPISODE = "ext_episode"
        private const val KEY_TITLE = "ext_title"
        private const val KEY_POSTER = "ext_poster"
        const val KEY_PENDING_RESULT = "ext_pending_result"
        private const val KEY_SEASONS = "ext_seasons"
        private const val KEY_VOICEOVER = "ext_voiceover"
        const val KEY_EXTERNAL_DURATION = "ext_external_duration"
    }

    init {
        savedStateHandle.get<String>(KEY_CONTENT_ID)?.let { restored ->
            contentId = restored
            pageUrl = savedStateHandle[KEY_PAGE_URL] ?: ""
            season = savedStateHandle[KEY_SEASON]
            episode = savedStateHandle[KEY_EPISODE]
            title = savedStateHandle[KEY_TITLE] ?: ""
            poster = savedStateHandle[KEY_POSTER] ?: ""
            voiceover = savedStateHandle[KEY_VOICEOVER]
            episodeId = if (season != null && episode != null) "s${season}e${episode}" else null
            AppLogger.d("PlayerVM", "Restored from SavedStateHandle: contentId=$contentId season=$season episode=$episode")
        }
        viewModelScope.launch {
            trackManager.availableTracks.collect { tracks ->
                if (tracks.isNotEmpty()) {
                    pendingTrackIndex = trackManager.selectedTrackIndex.value
                    rebuildPickerColumns()
                }
            }
        }
    }

    fun initialize(contentId: String, title: String, pageUrl: String, season: Int? = null, episode: Int? = null, poster: String = "") {
        val effectiveSeason = savedStateHandle.get<Int>(KEY_SEASON) ?: season
        val effectiveEpisode = savedStateHandle.get<Int>(KEY_EPISODE) ?: episode
        AppLogger.d("PickerVM", "initialize: effectiveSeason=$effectiveSeason effectiveEpisode=$effectiveEpisode contentId=$contentId status=${_state.value.status::class.simpleName}")
        if (_state.value.status is PlayerStatus.Ready || _state.value.status is PlayerStatus.Loading) {
            if (this.contentId == contentId && this.season == effectiveSeason && this.episode == effectiveEpisode) return
        }
        if (isResolving && this.contentId == contentId && this.season == effectiveSeason && this.episode == effectiveEpisode) return

        this.contentId = contentId
        this.title = title
        this.pageUrl = pageUrl
        this.poster = poster
        this.season = effectiveSeason
        this.episode = effectiveEpisode
        this.episodeId = if (effectiveSeason != null && effectiveEpisode != null) "s${effectiveSeason}e${effectiveEpisode}" else null
        selectedCodecMime = null
        crossProviderRetried = false

        if (seasons.isEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                watchProgressRepository.getSeasonsJson(contentId, episodeId)?.let { json ->
                    val restored = deserializeSeasons(json)
                    if (restored.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            seasons = restored
                            updateNavigationState()
                        }
                    }
                }
            }
        }

        savedStateHandle[KEY_CONTENT_ID] = contentId
        savedStateHandle[KEY_PAGE_URL] = pageUrl
        savedStateHandle[KEY_SEASON] = effectiveSeason
        savedStateHandle[KEY_EPISODE] = effectiveEpisode
        savedStateHandle[KEY_TITLE] = title
        savedStateHandle[KEY_POSTER] = poster
        savedStateHandle[KEY_VOICEOVER] = this.voiceover

        updateNavigationState()

        loadStream(pageUrl, if (effectiveSeason != null && effectiveEpisode != null) "S$effectiveSeason E$effectiveEpisode" else "")
    }

    private fun loadStream(url: String, subtitle: String, forceStartPosition: Long? = null) {
        PerformanceMonitor.begin("PlayerVM.loadStream")
        isAutoAdvancing = false  // скидаємо після завантаження
        deepJob?.cancel()
        loadJob?.cancel()
        preResolveJob?.cancel()
        lastSavedPosition = -1L
        isResolving = true

        engine?.let {
            it.pause()
        }

        _deepResolutionCompleted.value = false
        _state.update { it.copy(status = PlayerStatus.Loading(this.title), deepResolutionPending = true) }
        AppLogger.d("PickerVM", "loadStream: season=$season episode=$episode voiceover=$voiceover")
        loadJob = viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) {
                    watchProgressRepository.getStreamCache(contentId, episodeId)
                }
                if (cached != null) {
                    AppLogger.d("PickerVM", "Using cached stream URL: ${cached.streamUrl.take(60)}")
                    this@PlayerViewModel.availableStreams = (listOf(cached.streamUrl) + cached.fallbackUrls).distinct().toMutableList()
                    this@PlayerViewModel.referer = cached.referer
                    val pos = forceStartPosition
                        ?: withContext(Dispatchers.IO) { watchProgressRepository.getProgress(contentId, episodeId)?.positionMs }
                        ?: 0L
                    
                    _state.update { it.copy(
                        status = PlayerStatus.Ready(cached.streamUrl, this@PlayerViewModel.title, subtitle, pos, cached.referer, ua.ukrtv.app.domain.model.StreamType.valueOf(cached.streamType), loadTrigger = System.currentTimeMillis()),
                        availableSeasons = this@PlayerViewModel.seasons
                    ) }
                    updateNavigationState()
                    initPickerColumns()
                    isResolving = false
                    PerformanceMonitor.end()
                    launchDeepResolution()
                    preResolveNextEpisode()
                    return@launch
                }

                val res = streamResolvingInteractor.resolve(
                    url = url,
                    title = this@PlayerViewModel.title,
                    season = this@PlayerViewModel.season,
                    episode = this@PlayerViewModel.episode,
                    voiceover = this@PlayerViewModel.voiceover,
                    isDeep = false
                )
                if (res != null) {
                    val pos = forceStartPosition ?: getSavedPosition()
                    applyStreamResult(res, subtitle, pos)
                    preResolveNextEpisode()
                } else {
                    _state.update { it.copy(status = PlayerStatus.Error(appContext.getString(ua.ukrtv.app.R.string.video_not_found))) }
                }
            } catch (e: StreamResolutionException) {
                _state.update { it.copy(status = PlayerStatus.Error(e.message ?: "Помилка")) }
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

    private fun applyStreamResult(
        res: ua.ukrtv.app.domain.model.StreamResolutionResult,
        subtitle: String,
        pos: Long
    ) {
        availableStreams = (listOf(res.streamUrl) + res.fallbackStreams).distinct().toMutableList()
        referer = res.referer
        if (res.seasons != null) seasons = res.seasons
        _state.update { it.copy(
            status = PlayerStatus.Ready(res.streamUrl, title, subtitle, pos, res.referer, res.streamType, loadTrigger = System.currentTimeMillis()),
            availableSeasons = seasons
        ) }
        updateNavigationState()
        initPickerColumns()
        launchDeepResolution()
    }

    private fun launchDeepResolution() {
        deepJob?.cancel()
        _deepResolutionCompleted.value = false
        _state.update { it.copy(deepResolutionPending = true) }
        deepJob = viewModelScope.launch {
            try {
                var deepRes = try {
                    withContext(Dispatchers.IO) {
                        streamResolver.resolve(this@PlayerViewModel.pageUrl, isDeep = true)
                    }
                } catch (_: Exception) { null }

                if (deepRes?.seasons == null || deepRes.seasons.isEmpty()) {
                    AppLogger.w("PickerVM", "Deep resolution returned no seasons, retrying in 2s")
                    kotlinx.coroutines.delay(2000)
                    deepRes = try {
                        withContext(Dispatchers.IO) {
                            streamResolver.resolve(this@PlayerViewModel.pageUrl, isDeep = true)
                        }
                    } catch (_: Exception) { null }
                }

                val newSeasons = deepRes?.seasons
                if (newSeasons != null && newSeasons.isNotEmpty() && deepJob?.isActive == true) {
                    this@PlayerViewModel.seasons = newSeasons
                    if (this@PlayerViewModel.season == null && this@PlayerViewModel.episode == null) {
                        val firstSeason = newSeasons.first()
                        this@PlayerViewModel.season = firstSeason.number
                        this@PlayerViewModel.episode = firstSeason.episodes.firstOrNull()?.number ?: 1
                        this@PlayerViewModel.episodeId = "s${this@PlayerViewModel.season}e${this@PlayerViewModel.episode}"
                        savedStateHandle[KEY_SEASON] = this@PlayerViewModel.season
                        savedStateHandle[KEY_EPISODE] = this@PlayerViewModel.episode
                        AppLogger.d("PickerVM", "Deep resolution defaulted to S${this@PlayerViewModel.season}E${this@PlayerViewModel.episode}")
                    }
                    _state.update { it.copy(availableSeasons = newSeasons, deepResolutionPending = false) }
                    updateNavigationState()
                    rebuildPickerColumns()
                } else {
                    AppLogger.w("PickerVM", "Deep resolution failed after retry")
                    _state.update { it.copy(deepResolutionPending = false) }
                }
            } finally {
                _deepResolutionCompleted.value = true
                _state.update { it.copy(deepResolutionPending = false) }
            }
        }
    }

    private fun preResolveNextEpisode() {
        preResolveJob?.cancel()
        val nav = EpisodeNavigator.nextEpisode(seasons, season, episode) ?: return
        val nextEpisodeId = "s${nav.season}e${nav.episode}"

        preResolveJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    streamResolver.resolve(
                        url = pageUrl,
                        season = nav.season,
                        episode = nav.episode,
                        voiceover = voiceover,
                        isDeep = false
                    )
                }
                if (result != null && preResolveJob?.isActive == true) {
                    withContext(Dispatchers.IO) {
                        watchProgressRepository.saveProgress(
                            contentId = contentId,
                            episodeId = nextEpisodeId,
                            positionMs = 0L,
                            durationMs = 0L,
                            pageUrl = pageUrl,
                            streamUrl = result.streamUrl,
                            streamType = result.streamType.name,
                            referer = result.referer,
                            fallbackUrls = result.fallbackStreams.takeIf { it.isNotEmpty() }?.joinToString("|")
                        )
                    }
                    AppLogger.d("PlayerVM", "Pre-resolved next episode: ${nav.season}e${nav.episode} → ${result.streamUrl.take(60)}")
                }
            } catch (_: Exception) { }
        }
    }

    private fun updateNavigationState() {
        _state.update { it.copy(
            currentSeason = this.season,
            currentEpisode = this.episode,
            currentVoiceover = this.voiceover,
            availableSeasons = this.seasons
        ) }
    }

    private suspend fun getSavedPosition(): Long = withContext(Dispatchers.IO) {
        watchProgressRepository.getProgress(contentId, episodeId)?.positionMs ?: 0L
    }

    private var prefetchJob: kotlinx.coroutines.Job? = null

    private var lastSavedPosition: Long = -1L

    fun saveProgress(pos: Long, dur: Long) {
        if (dur <= 0) return
        if (pos == lastSavedPosition && pos > 0L) return
        lastSavedPosition = pos
        saveCurrentProgress(pos, dur)
        prefetchNextEpisodeIfNeeded(pos, dur)
    }

    private fun saveCurrentProgress(pos: Long, dur: Long) {
        val currentStatus = _state.value.status as? PlayerStatus.Ready
        val seasonsJson = if (this.seasons.isNotEmpty()) serializeSeasons(this.seasons) else null
        val fallbackUrls = availableStreams.drop(1).takeIf { it.isNotEmpty() }?.joinToString("|")
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                watchProgressRepository.saveProgress(
                    contentId, episodeId, pos, dur, title, poster, pageUrl,
                    currentStatus?.url, currentStatus?.streamType?.name, referer,
                    fallbackUrls, seasonsJson = seasonsJson
                )
            }
        }
    }

    private fun prefetchNextEpisodeIfNeeded(pos: Long, dur: Long) {
        if (prefetchJob?.isActive == true) return
        if (dur <= 0 || pos.toFloat() / dur < 0.85f) return
        val nav = EpisodeNavigator.nextEpisode(seasons, season, episode) ?: return
        prefetchJob = viewModelScope.launch {
            try {
                val nextEpisodeId = "s${nav.season}e${nav.episode}"
                val cached = withContext(Dispatchers.IO) {
                    watchProgressRepository.getStreamCache(contentId, nextEpisodeId)
                }
                val streamUrl = cached?.streamUrl ?: run {
                    val res = withContext(Dispatchers.IO) {
                        streamResolver.resolve(
                            url = pageUrl,
                            season = nav.season,
                            episode = nav.episode,
                            voiceover = voiceover,
                            isDeep = false
                        )
                    }
                    if (res != null) {
                        withContext(Dispatchers.IO) {
                            watchProgressRepository.saveProgress(
                                contentId = contentId, episodeId = nextEpisodeId,
                                positionMs = 0L, durationMs = 0L, pageUrl = pageUrl,
                                streamUrl = res.streamUrl, streamType = res.streamType.name,
                                referer = res.referer,
                                fallbackUrls = res.fallbackStreams.takeIf { it.isNotEmpty() }?.joinToString("|")
                            )
                        }
                        res.streamUrl
                    } else null
                }
                if (streamUrl != null) {
                    AppLogger.d("Warmup", "Prefetching next episode: ${streamUrl.take(60)}")
                    val prefReferer = ua.ukrtv.app.data.streaming.inferReferer(pageUrl)
                    val httpFactory = OkHttpDataSource.Factory(okHttpClient).setUserAgent(ua.ukrtv.app.Constants.USER_AGENT)
                    mediaPrefetcher.prefetch(appContext, streamUrl, mapOf("Referer" to prefReferer), httpFactory, viewModelScope)
                }
            } catch (_: Exception) {}
        }
    }


    fun togglePlay() { playbackManager.engine?.let { if (it.isPlaying) it.pause() else it.play() } }
    
    fun toggleMute() {
        playbackManager.toggleMute()
        _state.update { it.copy(isMuted = playbackManager.isMuted.value) }
    }
    
    fun setShowControls(show: Boolean) {
        _state.update { if (it.isShowingControls == show) it else it.copy(isShowingControls = show) }
    }
    
    fun retry() { loadStream(pageUrl, this.subtitle) }
    
    fun seekTo(positionMs: Long) {
        engine?.seekTo(positionMs)
    }

    fun updateCodecInfo(display: String, codecs: List<CodecInfo>) {
        _state.update { it.copy(currentCodecDisplay = display, availableCodecs = codecs) }
        rebuildPickerColumns()
    }

    fun initPickerColumns() {
        pendingSeason = this.season
        pendingEpisode = this.episode
        pendingVoiceover = this.voiceover
        rebuildPickerColumns()
    }

    fun rebuildPickerColumns() {
        val engineTracks = engine?.getVideoTracks()?.map { PlaybackTrackInfo(it.id, it.name) }?.toTypedArray() ?: emptyArray()
        val cols = episodePickerManager.rebuildPickerColumns(
            availableSeasons = _state.value.availableSeasons,
            currentSeason = this.season,
            currentEpisode = this.episode,
            currentVoiceover = this.voiceover,
            currentCodecDisplay = _state.value.currentCodecDisplay,
            trackManager = trackManager,
            engineVideoTracks = engineTracks
        )
        _state.update { prev ->
            if (prev.pickerColumns == cols) prev
            else prev.copy(pickerColumns = cols)
        }
    }

    fun onPickerColumnFocused(index: Int) {
        _state.update { if (it.pickerFocusedIndex == index) it else it.copy(pickerFocusedIndex = index) }
    }

    fun onPickerValueChange(direction: Int) {
        val idx = _state.value.pickerFocusedIndex
        val col = _state.value.pickerColumns.getOrNull(idx) ?: return
        when (col.id) {
            "season" -> changePendingSeason(direction)
            "episode" -> changePendingEpisode(direction)
            "voiceover" -> changePendingVoiceover(direction)
            "audio_mode" -> changeAudioMode(direction)
            "video_track" -> changePendingVideoTrack(direction)
            "codec" -> changeCodec(direction)
        }
    }

    fun onPickerCommit() {
        val idx = _state.value.pickerFocusedIndex
        val col = _state.value.pickerColumns.getOrNull(idx) ?: return
        if (!col.needsCommit) return
        when (col.id) {
            "season", "episode", "voiceover" -> {
                val seasons = _state.value.availableSeasons ?: return
                val s = pendingSeason ?: this.season ?: seasons.first().number
                val currentSeasonData = seasons.find { it.number == s } ?: seasons.first()
                val e = pendingEpisode ?: this.episode ?: currentSeasonData.episodes.firstOrNull()?.number ?: 1
                onEpisodeSelected(s, e, pendingVoiceover)
            }
        }
    }

    private fun changePendingSeason(direction: Int) {
        val seasons = _state.value.availableSeasons ?: return
        val current = pendingSeason ?: this.season ?: seasons.first().number
        val idx = seasons.indexOfFirst { it.number == current }
        if (idx == -1) return
        val newIdx = (idx + direction + seasons.size) % seasons.size
        pendingSeason = seasons[newIdx].number
        val newSeasonData = seasons[newIdx]
        pendingEpisode = newSeasonData.episodes.firstOrNull()?.number ?: 1
        val voOptions = newSeasonData.voiceoverOptions.filter { it.isNotBlank() }
        if (voOptions.isNotEmpty()) {
            pendingVoiceover = if (pendingVoiceover != null && voOptions.contains(pendingVoiceover)) {
                pendingVoiceover
            } else {
                voOptions.first()
            }
        }
        rebuildPickerColumns()
    }

    private fun changePendingEpisode(direction: Int) {
        val seasons = _state.value.availableSeasons ?: return
        val sNum = pendingSeason ?: this.season ?: seasons.first().number
        val season = seasons.find { it.number == sNum } ?: return
        val eps = season.episodes.sortedBy { it.number }
        if (eps.isEmpty()) return
        val current = pendingEpisode ?: this.episode ?: eps.first().number
        val idx = eps.indexOfFirst { it.number == current }
        if (idx == -1) return
        val newIdx = (idx + direction + eps.size) % eps.size
        pendingEpisode = eps[newIdx].number
        rebuildPickerColumns()
    }

    private fun changePendingVoiceover(direction: Int) {
        val seasons = _state.value.availableSeasons ?: return
        val sNum = pendingSeason ?: this.season ?: seasons.first().number
        val season = seasons.find { it.number == sNum } ?: return
        val options = season.voiceoverOptions.filter { it.isNotBlank() }
        if (options.size < 2) return
        val current = pendingVoiceover ?: this.voiceover ?: options.first()
        val idx = options.indexOf(current)
        if (idx == -1) return
        val newIdx = (idx + direction + options.size) % options.size
        pendingVoiceover = options[newIdx]
        rebuildPickerColumns()
    }

    private fun changePendingVideoTrack(direction: Int) {
        val tracks = trackManager.availableTracks.value
        val engineTracks = engine?.getVideoTracks() ?: emptyArray()
        if (tracks.isNotEmpty()) {
            val currentIdx = pendingTrackIndex ?: trackManager.selectedTrackIndex.value
            val newIdx = if (currentIdx == null) {
                if (direction > 0) 0 else tracks.size - 1
            } else {
                val raw = currentIdx + direction
                if (raw < 0 || raw >= tracks.size) null else raw
            }
            pendingTrackIndex = newIdx
            val p = player ?: return
            if (newIdx == null) trackManager.clearTrackOverride(p)
            else trackManager.selectTrack(tracks[newIdx], p)
        } else if (engineTracks.size > 1) {
            val currentIdx = pendingTrackIndex ?: 0
            val newIdx = (currentIdx + direction + engineTracks.size) % engineTracks.size
            pendingTrackIndex = newIdx
            engine?.setVideoTrack(engineTracks[newIdx].id)
        }
        rebuildPickerColumns()
    }

    private fun changeAudioMode(direction: Int) {
        val newMode = if (direction > 0) audioEngine.cycleMode() else audioEngine.cycleModeReverse()
        _state.update { it.copy(audioMode = newMode) }
        rebuildPickerColumns()
    }

    private fun changeCodec(direction: Int) {
        val codecs = _state.value.availableCodecs
        if (codecs.isEmpty()) return

        val currentIdx = if (selectedCodecMime == null) -1
            else codecs.indexOfFirst { it.mimeType == selectedCodecMime }

        val newIdx = if (currentIdx == -1) {
            if (direction > 0) 0 else codecs.size - 1
        } else {
            val next = currentIdx + direction
            if (next < 0 || next >= codecs.size) -1 else next
        }

        val p = player ?: return
        if (newIdx == -1) {
            selectedCodecMime = null
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .setPreferredVideoMimeType(null)
                .build()
        } else {
            selectedCodecMime = codecs[newIdx].mimeType
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .setPreferredVideoMimeType(selectedCodecMime)
                .build()
        }
    }

    fun onPlayerError(error: PlaybackException) {
        if (PlaybackErrorHandler.isFatalCodecDeath(error)) {
            AppLogger.w("PickerVM", "Fatal codec death: ${error.message}")
            if (!crossProviderRetried) {
                crossProviderRetried = true
                retryCount = 0
                viewModelScope.launch { 
                    val res = streamResolvingInteractor.searchAndResolveOnAlternateProvider(pageUrl, title, season, episode, voiceover)
                    if (res != null) {
                        applyStreamResult(res, subtitle, getSavedPosition())
                    } else {
                        _state.update { it.copy(status = PlayerStatus.Error("Кодек відтворення недоступний. Спробуйте пізніше.")) }
                    }
                }
                return
            }
            _state.update { it.copy(status = PlayerStatus.Error("Кодек відтворення недоступний. Спробуйте пізніше.")) }
            return
        }
        if (selectedCodecMime != null && PlaybackErrorHandler.isDecodingError(error)) {
            selectedCodecMime = null
            player?.let { p ->
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .setPreferredVideoMimeType(null)
                    .build()
            }
        }
        if (PlaybackErrorHandler.shouldFallbackStream(error)) {
            executeFallback("Stream error: ${error.errorCodeName}")
            return
        }
        if (PlaybackErrorHandler.shouldRetry(error) && retryCount < 2) {
            retryCount++
            loadStream(pageUrl, this.subtitle)
            return
        }
        if (!crossProviderRetried &&
            (PlaybackErrorHandler.isBlockedStream(error) || PlaybackErrorHandler.isNotFound(error) || currentStreamIndex >= availableStreams.size - 1)
        ) {
            crossProviderRetried = true
            retryCount = 0
            viewModelScope.launch { 
                val res = streamResolvingInteractor.searchAndResolveOnAlternateProvider(pageUrl, title, season, episode, voiceover)
                if (res != null) {
                    applyStreamResult(res, subtitle, getSavedPosition())
                } else {
                    _state.update { it.copy(status = PlayerStatus.Error(appContext.getString(ua.ukrtv.app.R.string.video_not_found))) }
                }
            }
            return
        }
        _state.update { it.copy(status = PlayerStatus.Error(PlaybackErrorHandler.getUserMessage(error))) }
    }

    fun onEngineError(message: String) {
        _state.update { it.copy(status = PlayerStatus.Error(message)) }
    }

    fun prepareNextEpisode(): Boolean {
        val nav = EpisodeNavigator.nextEpisode(seasons, season, episode) ?: return false
        preparedSeason = nav.season
        preparedEpisode = nav.episode
        return true
    }

    fun executePreparedNavigation() {
        if (isAutoAdvancing) return
        isAutoAdvancing = true
        val s = preparedSeason ?: return run { isAutoAdvancing = false }
        val e = preparedEpisode ?: return run { isAutoAdvancing = false }
        preparedSeason = null
        preparedEpisode = null
        this.season = s
        this.episode = e
        this.episodeId = "s${s}e${e}"
        savedStateHandle[KEY_SEASON] = s
        savedStateHandle[KEY_EPISODE] = e
        loadStream(pageUrl, "S$s E$e")
        updateNavigationState()
    }

    fun resetAutoAdvancing() {
        isAutoAdvancing = false
    }

    fun navigateToNextEpisode(): Boolean {
        preparedSeason = null
        preparedEpisode = null
        isAutoAdvancing = false
        val nav = EpisodeNavigator.nextEpisode(seasons, season, episode) ?: return false
        applyEpisodeNavigation(nav.season, nav.episode)
        return true
    }

    fun navigateToPreviousEpisode(): Boolean {
        val nav = EpisodeNavigator.previousEpisode(seasons, season, episode) ?: return false
        applyEpisodeNavigation(nav.season, nav.episode)
        return true
    }

    private fun applyEpisodeNavigation(season: Int, episode: Int) {
        this.season = season
        this.episode = episode
        this.episodeId = "s${season}e${episode}"
        loadStream(pageUrl, "S$season E$episode")
        updateNavigationState()
    }

    fun onEpisodeSelected(s: Int, e: Int, voiceover: String?) {
        this.voiceover = voiceover ?: this.voiceover
        isAutoAdvancing = false
        applyEpisodeNavigation(s, e)
    }

    fun hasNextEpisode(): Boolean = EpisodeNavigator.hasNextEpisode(seasons, season, episode)
    fun hasPreviousEpisode(): Boolean = EpisodeNavigator.hasPreviousEpisode(seasons, season, episode)

    fun getPreparedEpisode(): Pair<Int, Int>? {
        val s = preparedSeason ?: return null
        val e = preparedEpisode ?: return null
        return s to e
    }

    fun updatePlaybackState(state: Int) {
        _state.update { if (it.playbackState == state) it else it.copy(playbackState = state) }
    }

    fun updateIsPlaying(isPlaying: Boolean) {
        _state.update { if (it.isPlaying == isPlaying) it else it.copy(isPlaying = isPlaying) }
    }

    fun onBackgroundTransition(positionMs: Long, durationMs: Long) {
        savedBackgroundPosition = positionMs
        if (positionMs > 0 && durationMs > 0) {
            saveCurrentProgress(positionMs, durationMs)
        }
        AppLogger.d("PickerVM", "onBackgroundTransition: stopping player at ${positionMs}ms to release codec")
        engine?.pause()
    }

    fun onForegroundTransition() {
        val current = _state.value.status
        if (current is PlayerStatus.Ready) {
            val position = savedBackgroundPosition.takeIf { it > 0 } ?: current.positionMs
            AppLogger.d("PickerVM", "onForegroundTransition: resuming at ${position}ms")
            engine?.let { e ->
                if (position > 0) e.seekTo(position)
                e.play()
            }
        }
    }

    fun getOrCreatePlayer(context: Context, dsFactory: DataSource.Factory): ExoPlayer? {
        return playbackManager.getOrCreatePlayer(dsFactory)
    }

    fun getOrCreateEngine(context: Context): PlaybackEngine? {
        return playbackManager.getOrCreateEngine(playerPreferences.playerType.value, getDataSourceFactory())
    }

    private fun startMonitoring(engine: PlaybackEngine) {
        providerQualityManager.resetHealth()
        engine.addListener(object : PlaybackEngine.EngineListener {
            override fun onRebuffer() {
                providerQualityManager.onRebuffer()
                if (!providerQualityManager.healthState.isHealthy) {
                    handleUnhealthyStream()
                }
            }
            override fun onPositionChanged(positionMs: Long) {
                providerQualityManager.onPositionUpdate(positionMs)
            }
        })
        viewModelScope.launch {
            thermalMonitor.thermalStatus.collect { status ->
                val level = thermalMonitor.getQualityLevel(status)
                player?.let { playerFactory.applyThermalToPlayer(it, level) }
            }
        }
    }

    private fun executeFallback(reason: String) {
        if (currentStreamIndex < availableStreams.size - 1) {
            currentStreamIndex++
            val fallbackUrl = availableStreams[currentStreamIndex]
            AppLogger.w("PlayerVM", "$reason — trying fallback URL #${currentStreamIndex}: ${fallbackUrl.take(60)}")
            providerQualityManager.markHealthyForFallback()
            val currentStatus = _state.value.status
            if (currentStatus is PlayerStatus.Ready) {
                _state.update { it.copy(status = currentStatus.copy(url = fallbackUrl, loadTrigger = System.currentTimeMillis())) }
            }
        } else if (!crossProviderRetried) {
            crossProviderRetried = true
            retryCount = 0
            AppLogger.w("PlayerVM", "$reason — all fallback URLs exhausted, switching provider")
            providerQualityManager.markHealthyForFallback()
            providerQualityManager.markProviderSlow(providerManager.activeProvider.value.name)
            viewModelScope.launch {
                val pos = engine?.currentPosition ?: 0L
                saveProgress(pos, engine?.duration ?: 0L)
                val res = streamResolvingInteractor.searchAndResolveOnAlternateProvider(pageUrl, title, season, episode, voiceover)
                if (res != null) {
                    applyStreamResult(res, subtitle, pos)
                } else {
                    _state.update { it.copy(status = PlayerStatus.Error(appContext.getString(ua.ukrtv.app.R.string.video_not_found))) }
                }
            }
        }
    }

    private fun handleUnhealthyStream() {
        val reason = providerQualityManager.healthState.reason
        executeFallback(reason)
    }

    fun releaseEngine() {
        engine?.let { e ->
            if (e is ExoPlayerEngine) {
                val p = e.detachPlayer()
                if (p != null) p.release()
            } else {
                e.release()
            }
        }
        engine = null
        player = null
    }

    fun getDataSourceFactory(): DataSource.Factory {
        val httpFactory = OkHttpDataSource.Factory(okHttpClient).setUserAgent(ua.ukrtv.app.Constants.USER_AGENT)
        activeHttpFactory = httpFactory
        return mediaPrefetcher.getCachedDataSourceFactory(appContext, httpFactory)
    }

    fun releaseExternalPlayerLaunchLock() {
        synchronized(externalPlayerLaunchLock) {
            externalPlayerLaunchLocked = false
        }
    }

    private val externalPlayerLauncher_mock get() = ExternalPlayerLauncher(appContext)

    suspend fun createExternalPlayerIntent(): Intent? {
        val status = _state.value.status as? PlayerStatus.Ready ?: return null
        return externalPlayerInteractor.buildIntent(
            contentId = contentId,
            title = status.title,
            url = status.url,
            streamType = status.streamType,
            referer = status.referer,
            positionMs = status.positionMs,
            durationMs = savedStateHandle.get<Long>(KEY_EXTERNAL_DURATION) ?: 0L,
            season = season,
            episode = episode,
            voiceover = voiceover,
            seasons = seasons
        )
    }

    suspend fun handleExternalPlayerResult(resultCode: Int, data: Intent?): ExternalPlayerReturnResult {
        savedStateHandle[KEY_PENDING_RESULT] = false
        val result = externalPlayerInteractor.extractResult(resultCode, data)
        
        // Fallback: коли зовнішній плеєр не повертає дані (VLC service вже звільнив позицію)
        if (result == null || (result.positionMs == 0L && result.durationMs == 0L && !result.isFinished)) {
            val savedDur = savedStateHandle.get<Long>(KEY_EXTERNAL_DURATION) ?: 0L
            if (savedDur > 0) {
                // VLC зазвичай повертає 0/0 коли service вже звільнився після завершення відтворення
                // → користувач подивився до кінця
                saveProgress(savedDur, savedDur)
                if (hasNextEpisode()) {
                    advanceToNextEpisodeFromExternalPlayer()
                    return ExternalPlayerReturnResult.Advanced
                }
            }
            return ExternalPlayerReturnResult.NoData
        }

        AppLogger.d("PlayerVM", "External player result: code=$resultCode position=${result.positionMs} duration=${result.durationMs} finished=${result.isFinished} url=${result.url}")

        val durationMs = if (result.durationMs > 0) result.durationMs 
                         else savedStateHandle.get<Long>(KEY_EXTERNAL_DURATION) ?: 0L

        val isFinished = result.isFinished || (
            durationMs > 0 && result.positionMs > 0 &&
            result.positionMs.toFloat() / durationMs >= 0.90f
        )

        if (durationMs > 0) {
            saveProgress(if (isFinished) durationMs else result.positionMs, durationMs)
        }

        if (isFinished && seasons.isNotEmpty()) {
            if (this.season == null || this.episode == null) {
                val firstSeason = seasons.first()
                this.season = firstSeason.number
                this.episode = firstSeason.episodes.firstOrNull()?.number ?: 1
                this.episodeId = "s${this.season}e${this.episode}"
                savedStateHandle[KEY_SEASON] = this.season
                savedStateHandle[KEY_EPISODE] = this.episode
                AppLogger.d("PlayerVM", "Defaulted to S${this.season}E${this.episode}")
            }
            if (hasNextEpisode()) {
                advanceToNextEpisodeFromExternalPlayer()
                return ExternalPlayerReturnResult.Advanced
            }
        }
        
        return ExternalPlayerReturnResult.NotFinished(result.positionMs, durationMs)
    }

    fun switchToBuiltInPlayer(positionMs: Long) {
        _state.update { state ->
            val ready = state.status as? PlayerStatus.Ready ?: return@update state
            state.copy(status = ready.copy(positionMs = positionMs, loadTrigger = System.currentTimeMillis()))
        }
    }

    fun advanceToNextEpisodeFromExternalPlayer() {
        val nav = EpisodeNavigator.nextEpisode(seasons, season, episode) ?: return
        
        this.season = nav.season
        this.episode = nav.episode
        this.episodeId = "s${nav.season}e${nav.episode}"
        savedStateHandle[KEY_SEASON] = nav.season
        savedStateHandle[KEY_EPISODE] = nav.episode
        
        updateNavigationState()
        _state.update { it.copy(availableSeasons = this.seasons) }
        loadStream(pageUrl, "S${nav.season} E${nav.episode}", forceStartPosition = 0L)
    }

    suspend fun saveBeforeExternalPlayerLaunch() {
        externalPlayerLaunchTimeMs = System.currentTimeMillis()
        savedStateHandle[KEY_PENDING_RESULT] = true
        val currentStatus = _state.value.status as? PlayerStatus.Ready ?: return
        val pos = currentStatus.positionMs
        val dbDuration = withContext(Dispatchers.IO) {
            watchProgressRepository.getProgress(contentId, episodeId)?.durationMs
        }
        val dur = dbDuration?.takeIf { it > 0L }
            ?: engine?.duration?.takeIf { it > 0L }
        savedStateHandle[KEY_EXTERNAL_DURATION] = dur
        if (dur != null) {
            withContext(Dispatchers.IO) {
                val seasonsJson = if (this@PlayerViewModel.seasons.isNotEmpty()) serializeSeasons(this@PlayerViewModel.seasons) else null
                val fallbackUrls = availableStreams.drop(1).takeIf { it.isNotEmpty() }?.joinToString("|")
                watchProgressRepository.saveProgress(
                    contentId, episodeId, pos, dur, title, poster, pageUrl,
                    currentStatus.url, currentStatus.streamType.name, referer, fallbackUrls,
                    seasonsJson = seasonsJson
                )
            }
            lastSavedPosition = pos
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.release()
        audioEngine.release()
        loadJob?.cancel()
        prefetchJob?.cancel()
        deepJob?.cancel()
        preResolveJob?.cancel()
    }
}
