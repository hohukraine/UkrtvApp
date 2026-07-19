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

import ua.ukrtv.app.player.ThermalMonitor
import ua.ukrtv.app.util.PerformanceMonitor
import ua.ukrtv.app.util.PlayerPreferences
import ua.ukrtv.app.util.PlayerType
import ua.ukrtv.app.domain.model.StreamType
import android.content.Intent
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
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
    private val audioEngine: AudioEngine,
    private val providerManager: ProviderManager,
    private val playerPreferences: PlayerPreferences
) : ViewModel() {

    private val externalPlayerLauncher by lazy { ExternalPlayerLauncher(appContext) }

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

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

    var player: ExoPlayer? = null
        private set

    private var engine: PlaybackEngine? = null

    val currentEngine: PlaybackEngine? get() = engine

    private var pendingSeason: Int? = null
    private var pendingEpisode: Int? = null
    private var pendingVoiceover: String? = null
    private var pendingTrackIndex: Int? = null
    private var selectedCodecMime: String? = null

    val trackManager = TrackManager()

    val playerType: StateFlow<ua.ukrtv.app.util.PlayerType> = playerPreferences.playerType

    companion object {
        private const val KEY_CONTENT_ID = "ext_content_id"
        private const val KEY_PAGE_URL = "ext_page_url"
        private const val KEY_SEASON = "ext_season"
        private const val KEY_EPISODE = "ext_episode"
        private const val KEY_TITLE = "ext_title"
        private const val KEY_POSTER = "ext_poster"
        private const val KEY_PENDING_RESULT = "ext_pending_result"
        private const val KEY_SEASONS = "ext_seasons"
        private const val KEY_VOICEOVER = "ext_voiceover"
        private const val KEY_EXTERNAL_DURATION = "ext_external_duration"
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
            thermalMonitor.thermalStatus.collect { status ->
                val quality = thermalMonitor.getQualityLevel(status)
                AppLogger.d("ThermalMonitor", "Quality level: $quality (status=$status)")
                applyThermalQuality(quality)
                _state.update { it.copy(thermalStatus = status) }
            }
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

        player?.stop()
        player?.clearMediaItems()
        engine?.let {
            it.pause()
        }

        _state.update { it.copy(status = PlayerStatus.Loading(this.title)) }
        AppLogger.d("PickerVM", "loadStream: season=$season episode=$episode voiceover=$voiceover")
        loadJob = viewModelScope.launch {
            try {
                val cached: Triple<String, String, String>? = withContext(Dispatchers.IO) {
                    watchProgressRepository.getStreamCache(contentId, episodeId)
                }
                if (cached != null) {
                    val (cachedUrl, cachedType, cachedReferer) = cached
                    AppLogger.d("PickerVM", "Using cached stream URL: ${cachedUrl.take(60)}")
                    this@PlayerViewModel.availableStreams = mutableListOf(cachedUrl)
                    this@PlayerViewModel.referer = cachedReferer
                    val pos = forceStartPosition
                        ?: withContext(Dispatchers.IO) { watchProgressRepository.getProgress(contentId, episodeId)?.positionMs }
                        ?: 0L
                    _state.update { it.copy(
                        status = PlayerStatus.Ready(cachedUrl, this@PlayerViewModel.title, subtitle, pos, cachedReferer, ua.ukrtv.app.domain.model.StreamType.valueOf(cachedType), loadTrigger = System.currentTimeMillis()),
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
                    }
                    val pos = forceStartPosition ?: getSavedPosition()
                    _state.update { it.copy(
                        status = PlayerStatus.Ready(res.streamUrl, this@PlayerViewModel.title, subtitle, pos, res.referer, res.streamType, loadTrigger = System.currentTimeMillis()),
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
        deepJob = viewModelScope.launch {
            val deepRes = try {
                withContext(Dispatchers.IO) {
                    streamResolver.resolve(this@PlayerViewModel.pageUrl, isDeep = true)
                }
            } catch (_: Exception) { null }
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
                _state.update { it.copy(availableSeasons = newSeasons) }
                updateNavigationState()
                rebuildPickerColumns()
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
                        streamType = result.streamType.name
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
        viewModelScope.launch(Dispatchers.IO) {
            watchProgressRepository.saveProgress(contentId, episodeId, pos, dur, title, poster, pageUrl, streamUrl, streamType, referer)
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
        this.season = nav.season
        this.episode = nav.episode
        this.episodeId = "s${nav.season}e${nav.episode}"
        savedStateHandle[KEY_SEASON] = nav.season
        savedStateHandle[KEY_EPISODE] = nav.episode
        return true
    }

    fun executePreparedNavigation() {
        loadStream(pageUrl, "S$season E$episode")
        updateNavigationState()
    }

    fun navigateToNextEpisode(): Boolean {
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
            val streamUrl = (currentStatus as? PlayerStatus.Ready)?.url
            val streamType = (currentStatus as? PlayerStatus.Ready)?.streamType?.name
            viewModelScope.launch(Dispatchers.IO) {
                watchProgressRepository.saveProgress(contentId, episodeId, positionMs, durationMs, title, poster, pageUrl, streamUrl, streamType)
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

    fun releasePlayer(playerToRelease: ExoPlayer) {
        if (player === playerToRelease) player = null
        playerToRelease.release()
    }

    fun getOrCreatePlayer(context: Context, dsFactory: DataSource.Factory): ExoPlayer? {
        if (player == null) {
            try {
                player = playerFactory.buildPlayer(context, dsFactory)
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
        val currentType = playerPreferences.getPlayerType()
        if (engine != null) return engine
        engine = when (currentType) {
            PlayerType.BUILTIN -> {
                val dsFactory = getDataSourceFactory()
                val p = getOrCreatePlayer(context, dsFactory) ?: return null
                ExoPlayerEngine(p, dsFactory as androidx.media3.datasource.okhttp.OkHttpDataSource.Factory)
            }
            PlayerType.EXTERNAL_PLAYER -> null
        }
        return engine
    }

    fun releaseEngine() {
        engine?.release()
        engine = null
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

    fun createExternalPlayerIntent(): Intent? {
        val status = _state.value.status as? PlayerStatus.Ready ?: return null
        val packageName = playerPreferences.getExternalPlayerPackage()
        val playerInfo = externalPlayerLauncher.getPlayerInfo(packageName) ?: return null

        val currentSeason = seasons.find { it.number == (season ?: 1) }
        val currentVoiceover = voiceover ?: currentSeason?.voiceoverOptions?.firstOrNull()
        
        val allEpisodes = currentSeason?.voiceovers?.find { it.name == currentVoiceover }?.episodes
            ?.filter { ua.ukrtv.app.data.streaming.isDirectStreamUrl(it.url) }
            ?: emptyList()

        // Reorder playlist so current episode is first, or keep full list but ensure it's limited
        val currentIdx = allEpisodes.indexOfFirst { it.url == status.url }
        val playlist = if (currentIdx != -1) {
            // Include items from current index to the end
            allEpisodes.subList(currentIdx, allEpisodes.size).map {
                ExternalPlayerLauncher.PlaylistItem(
                    url = it.url,
                    title = "${status.title} - ${it.title}",
                    streamType = ua.ukrtv.app.data.streaming.getStreamType(it.url)
                )
            }
        } else {
            allEpisodes.map {
                ExternalPlayerLauncher.PlaylistItem(
                    url = it.url,
                    title = "${status.title} - ${it.title}",
                    streamType = ua.ukrtv.app.data.streaming.getStreamType(it.url)
                )
            }
        }

        val config = ExternalPlayerLauncher.PlayerLaunchConfig(
            streamUrl = status.url,
            streamType = status.streamType,
            title = status.title,
            referer = status.referer,
            positionMs = status.positionMs,
            durationMs = savedStateHandle.get<Long>(KEY_EXTERNAL_DURATION) ?: 0L,
            playlist = playlist
        )
        return externalPlayerLauncher.buildIntent(playerInfo, config)
    }

    fun openInExternalPlayer(): Boolean {
        val intent = createExternalPlayerIntent() ?: return false
        val ctx = appContext
        return try {
            ctx.startActivity(intent)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            val playerInfo = getCurrentExternalPlayerInfo()
            Toast.makeText(ctx, "Не знайдено зовнішній плеєр (${playerInfo?.label ?: ""})", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun handleExternalPlayerResult(resultCode: Int, data: Intent?): ExternalPlayerReturnResult {
        savedStateHandle[KEY_PENDING_RESULT] = false
        val result = externalPlayerLauncher.extractResult(resultCode, data) ?: return ExternalPlayerReturnResult.Error
        
        AppLogger.d("PlayerVM", "External player result: code=$resultCode position=${result.positionMs} duration=${result.durationMs} finished=${result.isFinished} seasonsSize=${seasons.size} season=$season episode=$episode")

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

        AppLogger.d("PlayerVM", "Re-evaluated isFinished=$isFinished (original=${result.isFinished} pos=${result.positionMs} dur=$durationMs)")

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
        loadStream(pageUrl, "S${nav.season} E${nav.episode}", forceStartPosition = 0L)
    }

    fun getCurrentExternalPlayerInfo(): ExternalPlayerInfo? {
        val packageName = playerPreferences.getExternalPlayerPackage()
        return externalPlayerLauncher.getPlayerInfo(packageName)
    }

    fun getInstalledExternalPlayers(): List<ExternalPlayerInfo> {
        return externalPlayerLauncher.detectInstalledPlayers()
    }

    fun isExternalPlayerInstalled(): Boolean {
        val packageName = playerPreferences.getExternalPlayerPackage()
        return externalPlayerLauncher.isInstalled(packageName)
    }

    fun hasPendingExternalPlayerResult(): Boolean =
        savedStateHandle.get<Boolean>(KEY_PENDING_RESULT) == true

    suspend fun saveBeforeExternalPlayerLaunch() {
        savedStateHandle[KEY_PENDING_RESULT] = true
        val currentStatus = _state.value.status as? PlayerStatus.Ready ?: return
        val pos = currentStatus.positionMs
        val dbDuration = withContext(Dispatchers.IO) {
            watchProgressRepository.getProgress(contentId, episodeId)?.durationMs
        }
        val dur = dbDuration?.takeIf { it > 0L }
            ?: engine?.duration?.takeIf { it > 0L }
            ?: maxOf(pos * 3, 60_000L)
        savedStateHandle[KEY_EXTERNAL_DURATION] = dur
        withContext(Dispatchers.IO) {
            watchProgressRepository.saveProgress(
                contentId, episodeId, pos, dur, title, poster, pageUrl,
                currentStatus.url, currentStatus.streamType.name, referer
            )
        }
    }

    fun setExternalPlayerPackage(packageName: String) {
        playerPreferences.setExternalPlayerPackage(packageName)
    }

    private fun serializeSeasons(seasons: List<Season>): String {
        val arr = JSONArray()
        for (s in seasons) {
            val voiceovers = JSONArray()
            for (v in s.voiceovers) {
                val episodes = JSONArray()
                for (ep in v.episodes) {
                    episodes.put(JSONObject().apply {
                        put("number", ep.number)
                        put("title", ep.title)
                        put("url", ep.url)
                        put("subtitles", ep.subtitles ?: JSONObject.NULL)
                        put("poster", ep.poster)
                    })
                }
                voiceovers.put(JSONObject().apply {
                    put("name", v.name)
                    put("episodes", episodes)
                })
            }
            arr.put(JSONObject().apply {
                put("number", s.number)
                put("voiceovers", voiceovers)
            })
        }
        return arr.toString()
    }

    private fun deserializeSeasons(json: String): List<Season> {
        return try {
            val arr = JSONArray(json)
            val result = mutableListOf<Season>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val voiceovers = mutableListOf<ua.ukrtv.app.domain.model.Voiceover>()
                val vArr = obj.getJSONArray("voiceovers")
                for (j in 0 until vArr.length()) {
                    val vObj = vArr.getJSONObject(j)
                    val episodes = mutableListOf<ua.ukrtv.app.domain.model.Episode>()
                    val eArr = vObj.getJSONArray("episodes")
                    for (k in 0 until eArr.length()) {
                        val eObj = eArr.getJSONObject(k)
                        episodes.add(ua.ukrtv.app.domain.model.Episode(
                            number = eObj.getInt("number"),
                            title = eObj.getString("title"),
                            url = eObj.getString("url"),
                            subtitles = if (eObj.isNull("subtitles")) null else eObj.getString("subtitles"),
                            poster = eObj.optString("poster", "")
                        ))
                    }
                    voiceovers.add(ua.ukrtv.app.domain.model.Voiceover(
                        name = vObj.getString("name"),
                        episodes = episodes
                    ))
                }
                result.add(Season(
                    number = obj.getInt("number"),
                    voiceovers = voiceovers
                ))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        engine?.release()
        engine = null
        player = null
        loadJob?.cancel()
        prefetchJob?.cancel()
        deepJob?.cancel()
        preResolveJob?.cancel()
    }
}
