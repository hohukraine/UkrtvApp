package ua.ukrtv.app.ui.player

import android.content.Context
import ua.ukrtv.app.util.AppLogger
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
import ua.ukrtv.app.player.PlaybackErrorHandler
import ua.ukrtv.app.player.PlayerWarmupManager
import ua.ukrtv.app.player.ThermalMonitor
import ua.ukrtv.app.util.PerformanceMonitor
import ua.ukrtv.app.util.PlayerPreferences
import ua.ukrtv.app.domain.model.StreamType
import android.content.Intent
import android.widget.Toast
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val watchProgressRepository: WatchProgressRepository,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val streamResolver: StreamResolver,
    private val playerFactory: PlayerFactory,
    private val thermalMonitor: ThermalMonitor,
    private val warmupManager: PlayerWarmupManager,
    private val audioEngine: AudioEngine,
    private val providerManager: ProviderManager,
    private val playerPreferences: PlayerPreferences
) : ViewModel() {

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
    private var seasons: List<Season>? = null

    private var loadJob: kotlinx.coroutines.Job? = null
    private var deepJob: kotlinx.coroutines.Job? = null
    private var isResolving = false
    var player: ExoPlayer? = null
        private set

    private var pendingSeason: Int? = null
    private var pendingEpisode: Int? = null
    private var pendingVoiceover: String? = null
    private var pendingTrackIndex: Int? = null
    private var selectedCodecMime: String? = null

    val trackManager = TrackManager()

    val playerType: StateFlow<ua.ukrtv.app.util.PlayerType> = playerPreferences.playerType

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
            trackManager.availableTracks.collect { tracks ->
                if (tracks.isNotEmpty()) {
                    pendingTrackIndex = trackManager.selectedTrackIndex.value
                    rebuildPickerColumns()
                }
            }
        }
    }

    fun initialize(contentId: String, title: String, pageUrl: String, season: Int? = null, episode: Int? = null, poster: String = "") {
        AppLogger.d("PickerVM", "initialize: season=$season episode=$episode contentId=$contentId status=${_state.value.status::class.simpleName}")
        if (_state.value.status is PlayerStatus.Ready || _state.value.status is PlayerStatus.Loading) {
            if (this.contentId == contentId && this.season == season && this.episode == episode) return
        }
        if (isResolving && this.contentId == contentId && this.season == season && this.episode == episode) return

        this.contentId = contentId
        this.title = title
        this.pageUrl = pageUrl
        this.poster = poster
        this.season = season
        this.episode = episode
        this.episodeId = if (season != null && episode != null) "s${season}e${episode}" else null
        selectedCodecMime = null
        crossProviderRetried = false

        updateNavigationState()

        loadStream(pageUrl, if (season != null && episode != null) "S$season E$episode" else "")
    }

    private fun loadStream(url: String, subtitle: String) {
        PerformanceMonitor.begin("PlayerVM.loadStream")
        deepJob?.cancel()
        loadJob?.cancel()
        isResolving = true

        player?.stop()
        player?.clearMediaItems()

        _state.update { it.copy(status = PlayerStatus.Loading(this.title)) }
        AppLogger.d("PickerVM", "loadStream: season=$season episode=$episode voiceover=$voiceover")
        loadJob = viewModelScope.launch {
            try {
                val res = streamResolver.resolve(url, season = this@PlayerViewModel.season, episode = this@PlayerViewModel.episode, voiceover = this@PlayerViewModel.voiceover, isDeep = false)
                if (res != null) {
                    this@PlayerViewModel.availableStreams = (listOf(res.streamUrl) + res.fallbackStreams).distinct().toMutableList()
                    this@PlayerViewModel.referer = res.referer
                    if (res.seasons != null) {
                        this@PlayerViewModel.seasons = res.seasons
                    }
                    val pos = getSavedPosition()
                    _state.update { it.copy(
                        status = PlayerStatus.Ready(res.streamUrl, this@PlayerViewModel.title, subtitle, pos, res.referer, res.streamType),
                        availableSeasons = this@PlayerViewModel.seasons
                    ) }
                    updateNavigationState()
                    initPickerColumns()
                    launchDeepResolution()
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
            val deepRes = try { streamResolver.resolve(this@PlayerViewModel.pageUrl, isDeep = true) } catch (_: Exception) { null }
            if (deepRes?.seasons?.isNotEmpty() == true && deepJob?.isActive == true) {
                this@PlayerViewModel.seasons = deepRes.seasons
                _state.update { it.copy(availableSeasons = deepRes.seasons) }
                updateNavigationState()
                rebuildPickerColumns()
            }
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

    fun saveProgress(pos: Long, dur: Long) {
        if (dur <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            watchProgressRepository.saveProgress(contentId, episodeId, pos, dur, title, poster, pageUrl)
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
                    val dsFactory = getDataSourceFactory()
                    warmupManager.warmup(url = res.streamUrl, dataSourceFactory = dsFactory)
                }
            } catch (_: Exception) {}
        }
    }


    fun togglePlay() { player?.let { if (it.isPlaying) it.pause() else it.play() } }
    
    fun toggleMute() {
        val newMuted = !_state.value.isMuted
        _state.update { it.copy(isMuted = newMuted) }
        player?.volume = if (newMuted) 0f else 1f
    }
    
    fun setShowControls(show: Boolean) {
        _state.update { it.copy(isShowingControls = show) }
    }
    
    fun retry() { loadStream(pageUrl, this.subtitle) }
    
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
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
            id = "scale_mode",
            label = "ФОРМАТ",
            value = _state.value.scaleMode.label
        ))

        cols.add(PickerColumn(
            id = "audio_mode",
            label = "АУДІО",
            value = audioEngine.getMode().label
        ))

        val tracks = trackManager.availableTracks.value
        if (tracks.isNotEmpty()) {
            val trackIdx = pendingTrackIndex ?: trackManager.selectedTrackIndex.value ?: 0
            val track = tracks.getOrNull(trackIdx)
            cols.add(PickerColumn(
                id = "video_track",
                label = "ТРЕК",
                value = track?.label?.substringBefore(" (") ?: "—"
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

        cols.add(PickerColumn(
            id = "external_player",
            label = "VLC",
            value = "Відкрити",
            needsCommit = true
        ))

        _state.update { it.copy(pickerColumns = cols) }
    }

    fun onPickerColumnFocused(index: Int) {
        _state.update { it.copy(pickerFocusedIndex = index) }
    }

    fun onPickerValueChange(direction: Int) {
        val idx = _state.value.pickerFocusedIndex
        val col = _state.value.pickerColumns.getOrNull(idx) ?: return
        when (col.id) {
            "season" -> changePendingSeason(direction)
            "episode" -> changePendingEpisode(direction)
            "voiceover" -> changePendingVoiceover(direction)
            "scale_mode" -> changePendingScaleMode(direction)
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
            "external_player" -> {
                openInExternalPlayer()
            }
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

    private fun changePendingScaleMode(direction: Int) {
        val newMode = if (_state.value.scaleMode == ScaleMode.FIT) ScaleMode.ZOOM else ScaleMode.FIT
        _state.update { it.copy(scaleMode = newMode) }
        rebuildPickerColumns()
    }

    private fun changePendingVideoTrack(direction: Int) {
        val tracks = trackManager.availableTracks.value
        if (tracks.isEmpty()) return
        val currentIdx = pendingTrackIndex ?: trackManager.selectedTrackIndex.value ?: 0
        val newIdx = (currentIdx + direction + tracks.size) % tracks.size
        pendingTrackIndex = newIdx
        val p = player ?: return
        trackManager.selectTrack(tracks[newIdx], p)
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
                val res = streamResolver.resolve(match.url, season = season, episode = episode, voiceover = voiceover, isDeep = false)
                if (res != null) {
                    availableStreams = (listOf(res.streamUrl) + res.fallbackStreams).distinct().toMutableList()
                    referer = res.referer
                    if (res.seasons != null) seasons = res.seasons
                    val pos = getSavedPosition()
                    _state.update { it.copy(
                        status = PlayerStatus.Ready(res.streamUrl, title, subtitle, pos, res.referer, res.streamType),
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

    fun updateProgress(positionMs: Long, durationMs: Long) {
        _state.update { it.copy(currentPosition = positionMs, duration = durationMs) }
    }

    fun updatePlaybackState(state: Int) {
        _state.update { it.copy(playbackState = state) }
    }

    fun updateIsPlaying(isPlaying: Boolean) {
        _state.update { it.copy(isPlaying = isPlaying) }
    }

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
            player?.let { audioEngine.attach(it) }
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

    fun openInExternalPlayer(): Boolean {
        val status = _state.value.status as? PlayerStatus.Ready ?: return false
        val ctx = appContext
        val mime = when (status.streamType) {
            StreamType.HLS -> "application/x-mpegURL"
            StreamType.MPD -> "application/dash+xml"
            StreamType.MP4 -> "video/mp4"
            else -> "video/*"
        }
        val uri = android.net.Uri.parse(status.url)

        val vlcIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            setPackage("org.videolan.vlc")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val intent = if (vlcIntent.resolveActivity(ctx.packageManager) != null) {
            vlcIntent
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return try {
            ctx.startActivity(intent)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(ctx, "Не знайдено зовнішній плеєр (VLC)", Toast.LENGTH_LONG).show()
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        player?.let {
            it.stop()
            it.release()
        }
        player = null
        loadJob?.cancel()
        prefetchJob?.cancel()
    }
}
