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
import ua.ukrtv.app.player.ProviderSpeedTester
import ua.ukrtv.app.player.ProviderScoreCache
import ua.ukrtv.app.player.StreamHealthMonitor
import ua.ukrtv.app.player.ThermalMonitor
import ua.ukrtv.app.player.PlayerPool

import ua.ukrtv.app.util.PerformanceMonitor
import ua.ukrtv.app.util.PlayerPreferences
import ua.ukrtv.app.util.PlayerType
import ua.ukrtv.app.domain.model.StreamType
import android.content.Intent
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext val appContext: Context,
    val savedStateHandle: SavedStateHandle,
    private val watchProgressRepository: WatchProgressRepository,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val streamResolver: StreamResolver,
    private val playerFactory: PlayerFactory,
    private val playerPool: PlayerPool,
    private val audioEngine: AudioEngine,
    private val providerManager: ProviderManager,
    val playerPreferences: PlayerPreferences,
    private val mediaPrefetcher: MediaPrefetcher,
    private val providerSpeedTester: ProviderSpeedTester,
    private val providerScoreCache: ProviderScoreCache,
    private val streamHealthMonitor: StreamHealthMonitor,
    private val thermalMonitor: ThermalMonitor
) : ViewModel() {

    val externalPlayerLauncher by lazy { ExternalPlayerLauncher(appContext) }

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
    private var seasons: List<Season> = run {
        val json = savedStateHandle.get<String>(KEY_SEASONS)
        if (json != null) deserializeSeasons(json) else emptyList()
    }
        set(value) {
            field = value
            savedStateHandle[KEY_SEASONS] = serializeSeasons(value)
        }

    private var loadJob: kotlinx.coroutines.Job? = null
    private var deepJob: kotlinx.coroutines.Job? = null
    private var preResolveJob: kotlinx.coroutines.Job? = null
    private var isResolving = false
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

    fun releaseExternalPlayerLaunchLock() {
        synchronized(externalPlayerLaunchLock) {
            externalPlayerLaunchLocked = false
        }
    }

    var player: ExoPlayer? = null
        private set

    private var engine: PlaybackEngine? = null

    val currentEngine: PlaybackEngine? get() = engine

    private var pendingSeason: Int? = null
    private var pendingEpisode: Int? = null
    private var pendingVoiceover: String? = null
    private var pendingTrackIndex: Int? = null
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
        private const val MINIMUM_EXTERNAL_PLAYBACK_MS = 5000L
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
                    if (this@PlayerViewModel.seasons.isEmpty()) {
                        val dbSeasonsJson = withContext(Dispatchers.IO) { watchProgressRepository.getSeasonsJson(contentId, episodeId) }
                        if (dbSeasonsJson != null) {
                            val restored = deserializeSeasons(dbSeasonsJson)
                            if (restored.isNotEmpty()) {
                                this@PlayerViewModel.seasons = restored
                                AppLogger.d("PickerVM", "Restored ${restored.size} seasons from DB")
                            }
                        }
                    }
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

                val res = withContext(Dispatchers.IO) {
                    streamResolver.resolve(url, season = this@PlayerViewModel.season, episode = this@PlayerViewModel.episode, voiceover = this@PlayerViewModel.voiceover, isDeep = false)
                }
                if (res != null) {
                    this@PlayerViewModel.availableStreams = (listOf(res.streamUrl) + res.fallbackStreams).distinct().toMutableList()
                    this@PlayerViewModel.referer = res.referer
                    if (res.seasons != null) {
                        this@PlayerViewModel.seasons = res.seasons
                    } else if (this@PlayerViewModel.seasons.isEmpty()) {
                        val dbSeasonsJson = withContext(Dispatchers.IO) { watchProgressRepository.getSeasonsJson(contentId, episodeId) }
                        if (dbSeasonsJson != null) {
                            val restored = deserializeSeasons(dbSeasonsJson)
                            if (restored.isNotEmpty()) {
                                this@PlayerViewModel.seasons = restored
                                AppLogger.d("PickerVM", "Restored ${restored.size} seasons from DB (non-cache path)")
                            }
                        }
                    }
                    val pos = forceStartPosition ?: getSavedPosition()

                    val bestRes = resolveWithBestProvider(url, res)
                    val finalRes = bestRes ?: res
                    val finalUrl = finalRes.streamUrl
                    val finalReferer = finalRes.referer
                    val finalStreamType = finalRes.streamType
                    if (bestRes != null) {
                        availableStreams = (listOf(bestRes.streamUrl) + bestRes.fallbackStreams).distinct().toMutableList()
                        referer = bestRes.referer
                        if (bestRes.seasons != null) seasons = bestRes.seasons
                    }

                    _state.update { it.copy(
                        status = PlayerStatus.Ready(finalUrl, this@PlayerViewModel.title, subtitle, pos, finalReferer, finalStreamType, loadTrigger = System.currentTimeMillis()),
                        availableSeasons = this@PlayerViewModel.seasons
                    ) }
                    updateNavigationState()
                    initPickerColumns()
                    launchDeepResolution()
                    preResolveNextEpisode()
                } else {
                    searchAndResolveOnAlternateProvider(url, subtitle)
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
        val currentStatus = _state.value.status
        val streamUrl = (currentStatus as? PlayerStatus.Ready)?.url
        val streamType = (currentStatus as? PlayerStatus.Ready)?.streamType?.name
        val seasonsJson = if (this.seasons.isNotEmpty()) serializeSeasons(this.seasons) else null
        val fallbackUrls = availableStreams.drop(1).takeIf { it.isNotEmpty() }?.joinToString("|")
        val saveContentId = contentId
        val saveEpisodeId = episodeId
        val saveTitle = title
        val savePoster = poster
        val savePageUrl = pageUrl
        val saveReferer = referer
        viewModelScope.launch(Dispatchers.IO) {
            watchProgressRepository.saveProgress(
                saveContentId, saveEpisodeId, pos, dur, saveTitle, savePoster, savePageUrl,
                streamUrl, streamType, saveReferer, fallbackUrls, seasonsJson = seasonsJson
            )
        }
        prefetchNextEpisodeIfNeeded(pos, dur)
    }

    private fun prefetchNextEpisodeIfNeeded(pos: Long, dur: Long) {
        if (prefetchJob?.isActive == true) return
        if (dur <= 0 || pos.toFloat() / dur < 0.85f) return
        val nav = EpisodeNavigator.nextEpisode(seasons, season, episode) ?: return
        prefetchJob = viewModelScope.launch {
            try {
                val res = streamResolver.resolve(
                    url = pageUrl,
                    season = nav.season,
                    episode = nav.episode,
                    voiceover = voiceover,
                    isDeep = false
                )
                if (res != null) {
                    AppLogger.d("Warmup", "Prefetched stream URL for: ${res.streamUrl.take(40)}")
                    val referer = ua.ukrtv.app.data.streaming.inferReferer(pageUrl)
                    val httpFactory = OkHttpDataSource.Factory(okHttpClient).setUserAgent(ua.ukrtv.app.Constants.USER_AGENT)
                    mediaPrefetcher.prefetch(appContext, res.streamUrl, mapOf("Referer" to referer), httpFactory, viewModelScope)
                }
            } catch (_: Exception) {}
        }
    }


    fun togglePlay() { engine?.let { if (it.isPlaying) it.pause() else it.play() } }
    
    fun toggleMute() {
        val newMuted = !_state.value.isMuted
        _state.update { it.copy(isMuted = newMuted) }
        engine?.setVolume(if (newMuted) 0f else 1f)
            ?: run { player?.volume = if (newMuted) 0f else 1f }
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
        val cols = mutableListOf<PickerColumn>()

        val seasons = _state.value.availableSeasons
        if (seasons != null && seasons.isNotEmpty()) {
            val allEpisodesAreOne = seasons.all { season ->
                season.episodes.all { it.number <= 1 }
            }

            if (pendingSeason == null) pendingSeason = this.season ?: seasons.first().number
            val sNum = pendingSeason!!
            val currentSeasonData = seasons.find { it.number == sNum } ?: seasons.first()
            val eps = currentSeasonData.episodes.sortedBy { it.number }

            if (pendingEpisode == null) pendingEpisode = this.episode ?: eps.firstOrNull()?.number ?: 1
            val eNum = pendingEpisode!!

            val voOptions = currentSeasonData.voiceoverOptions.filter { it.isNotBlank() }
            if (pendingVoiceover == null) {
                pendingVoiceover = this.voiceover.takeIf { it != null && voOptions.contains(it) } ?: voOptions.firstOrNull()
            }

            if (!allEpisodesAreOne) {
                cols.add(PickerColumn(
                    id = "season",
                    label = "СЕЗОН",
                    value = sNum.toString(),
                    needsCommit = true
                ))

                cols.add(PickerColumn(
                    id = "episode",
                    label = "СЕРІЯ",
                    value = eNum.toString(),
                    needsCommit = true
                ))
            }

            if (voOptions.size > 1) {
                val vo = pendingVoiceover ?: voOptions.first()
                cols.add(PickerColumn(
                    id = "voiceover",
                    label = "ОЗВУЧКА",
                    value = vo,
                    needsCommit = true
                ))
            }
        }

        cols.add(PickerColumn(
            id = "audio_mode",
            label = "АУДІО",
            value = audioEngine.getMode().label
        ))

        val tracks = trackManager.availableTracks.value
        val engineVideoTracks = engine?.getVideoTracks() ?: emptyArray()
        if (tracks.isNotEmpty()) {
            val selectedIdx = pendingTrackIndex ?: trackManager.selectedTrackIndex.value
            val value = if (selectedIdx == null) "Auto"
                else tracks.getOrNull(selectedIdx)?.label?.substringBefore(" (") ?: "Auto"
            cols.add(PickerColumn(
                id = "video_track",
                label = "ЯКІСТЬ",
                value = value
            ))
        } else if (engineVideoTracks.size > 1) {
            val trackIdx = pendingTrackIndex ?: 0
            val track = engineVideoTracks.getOrNull(trackIdx)
            cols.add(PickerColumn(
                id = "video_track",
                label = "ЯКІСТЬ",
                value = track?.name?.substringBefore(" (") ?: "—"
            ))
        }

        val codecDisplay = _state.value.currentCodecDisplay
        val codecs = _state.value.availableCodecs
        if (codecDisplay.isNotEmpty()) {
            cols.add(PickerColumn(
                id = "codec",
                label = "КОДЕК",
                value = codecDisplay
            ))
        }

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
            AppLogger.w("PickerVM", "Fatal codec death, skipping retries: ${error.message}")
            if (!crossProviderRetried) {
                crossProviderRetried = true
                retryCount = 0
                viewModelScope.launch { searchAndResolveOnAlternateProvider(pageUrl, subtitle) }
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
            if (currentStreamIndex < availableStreams.size - 1) {
                currentStreamIndex++
                val fallbackUrl = availableStreams[currentStreamIndex]
                val currentStatus = _state.value.status
                if (currentStatus is PlayerStatus.Ready) {
                    _state.update { it.copy(status = currentStatus.copy(url = fallbackUrl, loadTrigger = System.currentTimeMillis())) }
                    return
                }
            }
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
            viewModelScope.launch { searchAndResolveOnAlternateProvider(pageUrl, subtitle) }
            return
        }
        _state.update { it.copy(status = PlayerStatus.Error(PlaybackErrorHandler.getUserMessage(error))) }
    }

    fun onEngineError(message: String) {
        _state.update { it.copy(status = PlayerStatus.Error(message)) }
    }

    private suspend fun searchAndResolveOnAlternateProvider(originalUrl: String, subtitle: String) {
        val targetName = if (originalUrl.contains("uakino")) "Eneyida" else "Uakino"
        AppLogger.d("PickerVM", "Trying $targetName fallback for $title")
        try {
            val other = providerManager.availableProviders.find { it.name == targetName } ?: run {
                _state.update { it.copy(status = PlayerStatus.Error(appContext.getString(ua.ukrtv.app.R.string.video_not_found))) }
                return
            }
            val results = other.search(title, limit = 5)
            val match = results.firstOrNull()
            if (match != null) {
                val res = withContext(Dispatchers.IO) {
                    streamResolver.resolve(match.url, season = season, episode = episode, voiceover = voiceover, isDeep = false)
                }
                if (res != null) {
                    availableStreams = (listOf(res.streamUrl) + res.fallbackStreams).distinct().toMutableList()
                    referer = res.referer
                    if (res.seasons != null) seasons = res.seasons
                    val pos = getSavedPosition()
                    _state.update { it.copy(
                        status = PlayerStatus.Ready(res.streamUrl, title, subtitle, pos, res.referer, res.streamType, loadTrigger = System.currentTimeMillis()),
                        availableSeasons = seasons
                    ) }
                    updateNavigationState()
                    initPickerColumns()
                    launchDeepResolution()
                    return
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("PickerVM", "$targetName fallback error for $title", e)
        }
        _state.update { it.copy(status = PlayerStatus.Error(appContext.getString(ua.ukrtv.app.R.string.video_not_found))) }
    }

    fun prepareNextEpisode(): Boolean {
        val nav = EpisodeNavigator.nextEpisode(seasons, season, episode) ?: return false
        preparedSeason = nav.season
        preparedEpisode = nav.episode
        return true
    }

    fun executePreparedNavigation() {
        val s = preparedSeason ?: return
        val e = preparedEpisode ?: return
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

    fun navigateToNextEpisode(): Boolean {
        preparedSeason = null
        preparedEpisode = null
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
            val currentStatus = _state.value.status
            val readyStatus = currentStatus as? PlayerStatus.Ready
            val streamUrl = readyStatus?.url
            val streamType = readyStatus?.streamType?.name
            val streamReferer = readyStatus?.referer
            val seasonsJson = if (this.seasons.isNotEmpty()) serializeSeasons(this.seasons) else null
            val fallbackUrls = availableStreams.drop(1).takeIf { it.isNotEmpty() }?.joinToString("|")
            viewModelScope.launch(Dispatchers.IO) {
                watchProgressRepository.saveProgress(
                    contentId, episodeId, positionMs, durationMs, title, poster, pageUrl,
                    streamUrl, streamType, streamReferer, fallbackUrls, seasonsJson = seasonsJson
                )
            }
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
        if (player == null) {
            try {
                player = playerPool.acquire(context, dsFactory)
                player?.let { audioEngine.attach(it) }
            } catch (e: Exception) {
                AppLogger.e("PlayerViewModel", "Failed to create player", e)
                _state.update { it.copy(status = PlayerStatus.Error("Не вдалося ініціалізувати плеєр: ${e.message}", isRetryable = false)) }
                return null
            }
        }
        return player
    }

    fun getOrCreateEngine(context: Context): PlaybackEngine? {
        val currentType = playerPreferences.playerType.value
        if (engine != null) return engine
        engine = when (currentType) {
            PlayerType.BUILTIN -> {
                val dsFactory = getDataSourceFactory()
                val httpFactory = activeHttpFactory
                val p = getOrCreatePlayer(context, dsFactory) ?: return null
                ExoPlayerEngine(p, dsFactory, httpFactory)
            }
            PlayerType.EXTERNAL_PLAYER -> null
        }
        engine?.let { startMonitoring(it) }
        return engine
    }

    private fun startMonitoring(engine: PlaybackEngine) {
        streamHealthMonitor.reset()
        engine.addListener(object : PlaybackEngine.EngineListener {
            override fun onRebuffer() {
                streamHealthMonitor.onRebuffer()
                if (!streamHealthMonitor.currentState.isHealthy) {
                    handleUnhealthyStream()
                }
            }
            override fun onPositionChanged(positionMs: Long) {
                streamHealthMonitor.onPositionUpdate(positionMs)
            }
        })
        viewModelScope.launch {
            thermalMonitor.thermalStatus.collect { status ->
                val level = thermalMonitor.getQualityLevel(status)
                player?.let { playerFactory.applyThermalToPlayer(it, level) }
            }
        }
    }

    private fun handleUnhealthyStream() {
        if (currentStreamIndex >= availableStreams.size - 1 && !crossProviderRetried) {
            val reason = streamHealthMonitor.currentState.reason
            AppLogger.w("StreamHealth", "Unhealthy stream: $reason, switching provider")
            crossProviderRetried = true
            streamHealthMonitor.markHealthyWithoutReset()
            providerScoreCache.markSlow(providerManager.activeProvider.value.name)
            viewModelScope.launch {
                val pos = engine?.currentPosition ?: 0L
                saveProgress(pos, engine?.duration ?: 0L)
                searchAndResolveOnAlternateProvider(pageUrl, subtitle)
            }
        } else if (currentStreamIndex < availableStreams.size - 1) {
            AppLogger.w("StreamHealth", "Unhealthy stream, trying next fallback URL")
            currentStreamIndex++
            val fallbackUrl = availableStreams[currentStreamIndex]
            val currentStatus = _state.value.status
            if (currentStatus is PlayerStatus.Ready) {
                streamHealthMonitor.markHealthyWithoutReset()
                _state.update { it.copy(status = currentStatus.copy(url = fallbackUrl, loadTrigger = System.currentTimeMillis())) }
            }
        }
    }

    private suspend fun resolveWithBestProvider(
        originalUrl: String,
        primaryResult: ua.ukrtv.app.domain.model.StreamResolutionResult
    ): ua.ukrtv.app.domain.model.StreamResolutionResult? {
        return try {
            val targetName = if (originalUrl.contains("uakino")) "Eneyida" else "Uakino"
            val otherProvider = providerManager.availableProviders.find { it.name == targetName } ?: return null
            val primaryName = providerManager.activeProvider.value.name

            // Check cache first — skip HTTP tests if both scores are cached and valid
            val cachedPrimary = providerScoreCache.getScore(primaryName)
            val cachedOther = providerScoreCache.getScore(otherProvider.name)
            if (cachedPrimary != null && cachedOther != null) {
                val cachedDecision = providerSpeedTester.selectBest(
                    cachedPrimary.toSpeedTestResult(),
                    cachedOther.toSpeedTestResult()
                )
                if (cachedDecision != null && cachedDecision.providerName != primaryName) {
                    AppLogger.d("PickerVM", "Using faster provider (cached): ${cachedDecision.providerName}")
                    val otherRes = withContext(Dispatchers.IO) {
                        val results = otherProvider.search(title, limit = 5)
                        val match = results.firstOrNull() ?: return@withContext null
                        streamResolver.resolve(match.url, season = season, episode = episode, voiceover = voiceover, isDeep = false)
                    }
                    return otherRes
                }
                return null
            }

            val primaryTest = providerSpeedTester.testSpeed(
                providerName = primaryName,
                url = primaryResult.streamUrl,
                referer = primaryResult.referer
            )
            if (primaryTest != null) providerScoreCache.record(primaryTest.providerName, primaryTest)

            val otherRes = withContext(Dispatchers.IO) {
                val results = otherProvider.search(title, limit = 5)
                val match = results.firstOrNull() ?: return@withContext null
                streamResolver.resolve(match.url, season = season, episode = episode, voiceover = voiceover, isDeep = false)
            } ?: return null

            val otherTest = providerSpeedTester.testSpeed(
                providerName = otherProvider.name,
                url = otherRes.streamUrl,
                referer = otherRes.referer
            )
            if (otherTest != null) providerScoreCache.record(otherTest.providerName, otherTest)

            val best = providerSpeedTester.selectBest(primaryTest, otherTest)
            if (best != null && best.providerName != primaryName) {
                AppLogger.d("PickerVM", "Using faster provider: ${best.providerName} (throughput=${"%.0f".format(best.throughputKbps)}KB/s)")
                otherRes
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.d("PickerVM", "Provider speed comparison failed: ${e.message}")
            null
        }
    }

    private fun ProviderScoreCache.ProviderScore.toSpeedTestResult() = ProviderSpeedTester.SpeedTestResult(
        providerName = providerName,
        url = url,
        timeToFirstByteMs = timeToFirstByteMs,
        throughputKbps = throughputKbps,
        timestamp = timestamp
    )

    fun releaseEngine() {
        engine?.let { e ->
            if (e is ExoPlayerEngine) {
                val p = e.detachPlayer()
                if (p != null) playerPool.release(p)
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

    fun createExternalPlayerIntent(): Intent? {
        val status = _state.value.status as? PlayerStatus.Ready ?: return null
        val packageName = playerPreferences.externalPlayerPackage.value
        val playerInfo = externalPlayerLauncher.getPlayerInfo(packageName) ?: return null

        val currentSeason = seasons.find { it.number == (season ?: 1) }
        val currentVoiceover = voiceover ?: currentSeason?.voiceoverOptions?.firstOrNull()

        val allEpisodes = currentSeason
            ?.let { season ->
                season.voiceovers
                    .let { voiceovers ->
                        voiceovers.find { it.name == currentVoiceover }
                            ?: voiceovers.find { currentVoiceover != null && it.name.equals(currentVoiceover, ignoreCase = true) }
                            ?: voiceovers.find { currentVoiceover != null && it.name.contains(currentVoiceover, ignoreCase = true) }
                            ?: voiceovers.firstOrNull()
                    }?.episodes
                    ?.filter { ua.ukrtv.app.data.streaming.isLikelyPlayableUrl(it.url) }
                    ?: season.episodes.filter { ua.ukrtv.app.data.streaming.isLikelyPlayableUrl(it.url) }
            }
            ?: emptyList()

        val currentEpisode = episode
        val currentSeasonNum = season
        val effectiveEpisodes = if (allEpisodes.isEmpty() && currentEpisode != null) {
            listOf(ua.ukrtv.app.domain.model.Episode(number = currentEpisode, title = "S${currentSeasonNum ?: 1}E$currentEpisode", url = status.url))
        } else {
            allEpisodes
        }

        // Reorder playlist so current episode is first, or keep full list but ensure it's limited
        val currentEpisodeNum = this.episode ?: 1
        val currentIdx = effectiveEpisodes.indexOfFirst { it.number == currentEpisodeNum }
            .takeIf { it >= 0 } ?: 0
        val playlist = effectiveEpisodes.map {
            ExternalPlayerLauncher.PlaylistItem(
                url = it.url,
                title = "${status.title} - ${it.title}",
                streamType = ua.ukrtv.app.data.streaming.getStreamType(it.url)
            )
        }

        val config = ExternalPlayerLauncher.PlayerLaunchConfig(
            streamUrl = status.url,
            streamType = status.streamType,
            title = status.title,
            referer = status.referer,
            positionMs = status.positionMs,
            durationMs = savedStateHandle.get<Long>(KEY_EXTERNAL_DURATION) ?: 0L,
            playlist = playlist,
            playlistCurrentIndex = if (currentIdx != -1) currentIdx else 0
        )
        return externalPlayerLauncher.buildIntent(playerInfo, config)
    }

    fun handleExternalPlayerResult(resultCode: Int, data: Intent?): ExternalPlayerReturnResult {
        savedStateHandle[KEY_PENDING_RESULT] = false
        val result = externalPlayerLauncher.extractResult(resultCode, data) ?: return ExternalPlayerReturnResult.Error
        
        AppLogger.d("PlayerVM", "External player result: code=$resultCode position=${result.positionMs} duration=${result.durationMs} finished=${result.isFinished} url=${result.url} seasonsSize=${seasons.size} season=$season episode=$episode")

        if (result.positionMs == 0L && result.durationMs == 0L && !result.isFinished) {
            AppLogger.d("PlayerVM", "External player returned no data (0/0) and not finished, keeping pre-launch save")
            return ExternalPlayerReturnResult.NoData
        }

        val durationMs = if (result.durationMs > 0) {
            result.durationMs
        } else {
            savedStateHandle.get<Long>(KEY_EXTERNAL_DURATION) ?: 0L
        }

        val isFinished = result.isFinished || (
            durationMs > 0 && result.positionMs > 0 &&
            result.positionMs.toFloat() / durationMs >= 0.90f
        )

        val activeMs = if (externalPlayerLaunchTimeMs > 0L) {
            System.currentTimeMillis() - externalPlayerLaunchTimeMs
        } else {
            Long.MAX_VALUE
        }

        val endBy = try { data?.extras?.getString("end_by") } catch (_: Exception) { null }

        if (isFinished && result.positionMs == 0L && activeMs < MINIMUM_EXTERNAL_PLAYBACK_MS && endBy != "playback_completion") {
            AppLogger.w("PlayerVM", "External player returned completion after ${activeMs}ms with pos=0 (end_by=$endBy) — treating as error (minimum=${MINIMUM_EXTERNAL_PLAYBACK_MS}ms)")
            externalPlayerLaunchTimeMs = 0L
            return ExternalPlayerReturnResult.Error
        }

        AppLogger.d("PlayerVM", "Re-evaluated isFinished=$isFinished (original=${result.isFinished} pos=${result.positionMs} dur=$durationMs activeMs=$activeMs)")

        // Identify which episode was playing based on the returned URL
        val resultUrl = result.url
        var targetSeason = this.season
        var targetEpisode = this.episode

        if (resultUrl != null) {
            val matchedEpisode = seasons.flatMap { s -> s.episodes.map { s.number to it } }
                .find { it.second.url == resultUrl }
            
            if (matchedEpisode != null) {
                val oldS = targetSeason
                val oldE = targetEpisode
                targetSeason = matchedEpisode.first
                targetEpisode = matchedEpisode.second.number
                
                if (targetSeason != oldS || targetEpisode != oldE) {
                    AppLogger.d("PlayerVM", "External player advanced from S${oldS}E${oldE} to S${targetSeason}E${targetEpisode}")
                    
                    // Mark previous episodes as finished
                    markIntermediateEpisodesAsFinished(oldS, oldE, targetSeason, targetEpisode)
                    
                    // Update current context to the new episode
                    this.season = targetSeason
                    this.episode = targetEpisode
                    this.episodeId = "s${targetSeason}e${targetEpisode}"
                    savedStateHandle[KEY_SEASON] = targetSeason
                    savedStateHandle[KEY_EPISODE] = targetEpisode
                }
            }
        }

        if (durationMs > 0) {
            val positionMs = if (isFinished && result.positionMs == 0L) {
                durationMs
            } else {
                result.positionMs
            }
            saveProgress(positionMs, durationMs)
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
                try {
                    advanceToNextEpisodeFromExternalPlayer()
                    return ExternalPlayerReturnResult.Advanced
                } catch (e: Exception) {
                    AppLogger.e("PlayerVM", "Failed to advance to next episode: ${e.message}", e)
                }
            }
        }
        return ExternalPlayerReturnResult.NotFinished(result.positionMs, durationMs)
    }

    private fun markIntermediateEpisodesAsFinished(fromS: Int?, fromE: Int?, toS: Int, toE: Int) {
        if (fromS == null || fromE == null) return
        
        viewModelScope.launch(Dispatchers.IO) {
            val allEpisodes = seasons.flatMap { s -> s.episodes.map { Triple(s.number, it.number, it) } }
            val startIndex = allEpisodes.indexOfFirst { it.first == fromS && it.second == fromE }
            val endIndex = allEpisodes.indexOfFirst { it.first == toS && it.second == toE }
            
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                for (i in startIndex until endIndex) {
                    val ep = allEpisodes[i]
                    val epId = "s${ep.first}e${ep.second}"
                    // Mock duration if unknown, 1h
                    val dur = 3600_000L
                    AppLogger.d("PlayerVM", "Marking intermediate episode $epId as finished")
                    watchProgressRepository.saveProgress(
                        contentId, epId, dur, dur, title, poster, pageUrl,
                        ep.third.url, ua.ukrtv.app.data.streaming.getStreamType(ep.third.url).name, referer
                    )
                }
            }
        }
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
        val seasonsJson = if (this.seasons.isNotEmpty()) serializeSeasons(this.seasons) else null
        val fallbackUrls = availableStreams.drop(1).takeIf { it.isNotEmpty() }?.joinToString("|")
        if (dur != null) {
            withContext(Dispatchers.IO) {
                watchProgressRepository.saveProgress(
                    contentId, episodeId, pos, dur, title, poster, pageUrl,
                    currentStatus.url, currentStatus.streamType.name, referer, fallbackUrls,
                    seasonsJson = seasonsJson
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        engine?.let { e ->
            if (e is ExoPlayerEngine) {
                val p = e.detachPlayer()
                if (p != null) playerPool.release(p)
            } else {
                e.release()
            }
        }
        engine = null
        player = null
        loadJob?.cancel()
        prefetchJob?.cancel()
        deepJob?.cancel()
        preResolveJob?.cancel()
    }
}
