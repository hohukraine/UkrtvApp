package ua.ukrtv.app.ui.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.ukrtv.app.data.repository.WatchProgressRepository
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.domain.model.MediaLaunchState
import ua.ukrtv.app.player.PlaybackErrorHandler
import ua.ukrtv.app.player.PlayerFactory
import ua.ukrtv.app.player.CodecPolicy
import ua.ukrtv.app.player.PlaybackStatsTracker
import android.view.Surface
import ua.ukrtv.app.player.AutoFrameRateHelper
import ua.ukrtv.app.util.AppLogger
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
        val loadTrigger: Long = System.currentTimeMillis()
    ) : PlayerUiState()
    data class Error(val message: String, val category: PlaybackErrorHandler.ErrorCategory, val isRetryable: Boolean = true) : PlayerUiState()
    data class SeriesSelection(val seasons: List<Season>) : PlayerUiState()
}

data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val type: Int = C.TRACK_TYPE_VIDEO
)

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
    private val watchProgressRepository: WatchProgressRepository,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val streamResolver: StreamResolver,
    private val playerFactory: PlayerFactory,
    private val playbackStatsTracker: PlaybackStatsTracker,
    private val autoFrameRateHelper: AutoFrameRateHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val uiState: StateFlow<PlayerUiState> = _uiState

    private val _showControls = MutableStateFlow(true)
    val showControls: StateFlow<Boolean> = _showControls

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _playbackState = MutableStateFlow(androidx.media3.common.Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState

    private val _codecPolicy = MutableStateFlow(CodecPolicy.AUTO)
    val codecPolicy: StateFlow<CodecPolicy> = _codecPolicy

    private val _availableTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val availableTracks: StateFlow<List<TrackInfo>> = _availableTracks

    private val _selectedTrackIndex = MutableStateFlow<Int?>(null)
    val selectedTrackIndex: StateFlow<Int?> = _selectedTrackIndex

    private val _availableAudioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val availableAudioTracks: StateFlow<List<TrackInfo>> = _availableAudioTracks

    private val _selectedAudioTrackIndex = MutableStateFlow<Int?>(null)
    val selectedAudioTrackIndex: StateFlow<Int?> = _selectedAudioTrackIndex

    private val _availableSubtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val availableSubtitleTracks: StateFlow<List<TrackInfo>> = _availableSubtitleTracks

    private val _selectedSubtitleTrackIndex = MutableStateFlow<Int?>(null)
    val selectedSubtitleTrackIndex: StateFlow<Int?> = _selectedSubtitleTrackIndex

    private var context = PlayerContext()
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    val playerContext: PlayerContext get() = context

    private val _recreateSignal = MutableStateFlow(0L)
    val recreateSignal: StateFlow<Long> = _recreateSignal

    data class PlayerContext(
        var contentId: String = "",
        var title: String = "",
        var pageUrl: String = "",
        var poster: String = "",
        var referer: String = "",
        var subtitle: String = "",
        var season: Int? = null,
        var episode: Int? = null,
        var episodeId: String? = null,
        var retryCount: Int = 0,
        var availableStreams: MutableList<String> = mutableListOf(),
        var currentStreamIndex: Int = 0,
        var seasons: List<Season>? = null
    )

    fun initialize(contentId: String, title: String, pageUrl: String, season: Int? = null, episode: Int? = null, poster: String = "") {
        context = PlayerContext(
            contentId = contentId,
            title = title,
            pageUrl = pageUrl,
            poster = poster,
            season = season,
            episode = episode,
            episodeId = if (season != null && episode != null) "s${season}e${episode}" else null
        )

        val preResolved = savedStateHandle.get<MediaLaunchState.Ready>("launch_state")
        if (preResolved != null && preResolved.contentId == contentId && preResolved.season == season && preResolved.episode == episode) {
            AppLogger.d("PlayerViewModel", "Using pre-resolved launch state for $title")
            val res = preResolved.streamResult
            context.referer = res.referer
            if (preResolved.posterUrl.isNotEmpty()) context.poster = preResolved.posterUrl
            context.subtitle = if (season != null && episode != null) "S$season E$episode" else ""
            context.seasons = preResolved.seasons ?: res.seasons
            _uiState.value = PlayerUiState.Ready(
                url = res.streamUrl,
                title = context.title,
                subtitle = context.subtitle,
                positionMs = 0L,
                referer = res.referer,
                streamType = res.streamType
            )
        } else {
            loadStream(pageUrl, if (season != null && episode != null) "S$season E$episode" else "")
        }
    }

    fun loadStream(url: String, subtitle: String) {
        context.subtitle = subtitle
        _uiState.value = PlayerUiState.Loading(context.title)
        viewModelScope.launch {
            val res = try {
                withContext(Dispatchers.IO) {
                    streamResolver.resolve(
                        url = url,
                        referer = "",
                        season = context.season,
                        episode = context.episode
                    )
                }
            } catch (e: Exception) {
                Log.w("PlayerViewModel", "Stream resolve failed: ${e.message}")
                null
            }
            if (res != null) {
                context.availableStreams.clear()
                val primaryUrl = res.streamUrl
                if (primaryUrl.contains(".m3u8") || primaryUrl.contains(".mpd") || primaryUrl.contains(".mp4")) {
                    context.availableStreams.add(primaryUrl)
                }
                context.availableStreams.addAll(res.fallbackStreams)
                context.availableStreams = context.availableStreams.distinct().toMutableList()
                context.currentStreamIndex = 0
                context.referer = res.referer
                context.seasons = res.seasons
                val position = getSavedPosition()
                _uiState.value = PlayerUiState.Ready(
                    url = res.streamUrl,
                    title = context.title,
                    subtitle = subtitle,
                    positionMs = position,
                    referer = res.referer,
                    streamType = res.streamType
                )
            } else {
                _uiState.value = PlayerUiState.Error(
                    appContext.getString(ua.ukrtv.app.R.string.video_not_found),
                    PlaybackErrorHandler.ErrorCategory.UNKNOWN
                )
            }
        }
    }

    private suspend fun getSavedPosition(): Long {
        return withContext(Dispatchers.IO) {
            watchProgressRepository.getProgress(context.contentId, context.episodeId)?.positionMs ?: 0L
        }
    }

    fun saveProgress(pos: Long, dur: Long) {
        if (dur <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            watchProgressRepository.saveProgress(context.contentId, context.episodeId, pos, dur, context.title, context.poster, context.pageUrl)
        }
    }

    fun onPlayerError(error: PlaybackException, pos: Long) {
        val category = PlaybackErrorHandler.getErrorCategory(error)
        val userMsg = PlaybackErrorHandler.getUserMessage(error)

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

        if (PlaybackErrorHandler.shouldRetry(error) || PlaybackErrorHandler.isBlockedStream(error)) {
            if (context.retryCount < 2) {
                context.retryCount++
                if (context.pageUrl.isNotEmpty()) {
                    loadStream(context.pageUrl, context.subtitle)
                    return
                }
            }
        }

        _uiState.value = PlayerUiState.Error(userMsg, category, isRetryable = true)
    }

    fun retry() {
        context.retryCount = 0
        val subtitle = if (context.season != null && context.episode != null) "S${context.season} E${context.episode}" else ""
        loadStream(context.pageUrl, subtitle)
    }

    fun navigateToNextEpisode(): Boolean {
        val seasons = context.seasons ?: return false
        val currentSeason = context.season ?: return false
        val currentEpisode = context.episode ?: return false

        val seasonIdx = seasons.indexOfFirst { it.number == currentSeason }
        if (seasonIdx == -1) return false

        val season = seasons[seasonIdx]
        val epIdx = season.episodes.indexOfFirst { it.number == currentEpisode }

        if (epIdx >= 0 && epIdx < season.episodes.size - 1) {
            val nextEp = season.episodes[epIdx + 1]
            context.season = currentSeason
            context.episode = nextEp.number
            context.episodeId = "s${currentSeason}e${nextEp.number}"
            context.retryCount = 0
            loadStream(context.pageUrl, "S$currentSeason E${nextEp.number}")
            return true
        }

        if (seasonIdx < seasons.size - 1) {
            val nextSeason = seasons[seasonIdx + 1]
            if (nextSeason.episodes.isNotEmpty()) {
                val firstEp = nextSeason.episodes[0]
                context.season = nextSeason.number
                context.episode = firstEp.number
                context.episodeId = "s${nextSeason.number}e${firstEp.number}"
                context.retryCount = 0
                loadStream(context.pageUrl, "S${nextSeason.number} E${firstEp.number}")
                return true
            }
        }

        return false
    }

    fun navigateToPreviousEpisode(): Boolean {
        val seasons = context.seasons ?: return false
        val currentSeason = context.season ?: return false
        val currentEpisode = context.episode ?: return false

        val seasonIdx = seasons.indexOfFirst { it.number == currentSeason }
        if (seasonIdx == -1) return false

        val season = seasons[seasonIdx]
        val epIdx = season.episodes.indexOfFirst { it.number == currentEpisode }

        if (epIdx > 0) {
            val prevEp = season.episodes[epIdx - 1]
            context.season = currentSeason
            context.episode = prevEp.number
            context.episodeId = "s${currentSeason}e${prevEp.number}"
            context.retryCount = 0
            loadStream(context.pageUrl, "S$currentSeason E${prevEp.number}")
            return true
        }

        if (seasonIdx > 0) {
            val prevSeason = seasons[seasonIdx - 1]
            if (prevSeason.episodes.isNotEmpty()) {
                val lastEp = prevSeason.episodes.last()
                context.season = prevSeason.number
                context.episode = lastEp.number
                context.episodeId = "s${prevSeason.number}e${lastEp.number}"
                context.retryCount = 0
                loadStream(context.pageUrl, "S${prevSeason.number} E${lastEp.number}")
                return true
            }
        }

        return false
    }

    fun toggleCodecPolicy() {
        val next = when (_codecPolicy.value) {
            CodecPolicy.AUTO -> CodecPolicy.SOFTWARE_FIRST
            CodecPolicy.HARDWARE_FIRST -> CodecPolicy.SOFTWARE_FIRST
            CodecPolicy.SOFTWARE_FIRST -> CodecPolicy.AUTO
        }
        _codecPolicy.value = next
        _recreateSignal.value = System.currentTimeMillis()
    }

    fun setIsPlaying(p: Boolean) {
        _isPlaying.value = p
        if (p) autoFrameRateHelper.onPlaybackStarted()
    }

    override fun onCleared() {
        super.onCleared()
        playbackStatsTracker.stopTracking()
        autoFrameRateHelper.release()
    }
    fun updatePlaybackState(s: Int) { _playbackState.value = s }
    fun setShowControls(s: Boolean) { _showControls.value = s }
    fun setSurface(surface: Surface?) { autoFrameRateHelper.setSurface(surface) }
    fun logAfrEnvironment(tag: String) { autoFrameRateHelper.logEnvironment(tag) }
    fun startTracking(player: ExoPlayer) { viewModelScope.launch { playbackStatsTracker.startTracking(player, watchProgressRepository.getDeviceId(), context.contentId, context.episodeId) } }
    fun stopTracking() = playbackStatsTracker.stopTracking()

    fun hasNextEpisode(): Boolean {
        val seasons = context.seasons ?: return false
        val s = context.season ?: return false
        val e = context.episode ?: return false
        val si = seasons.indexOfFirst { it.number == s }
        if (si == -1) return false
        val ei = seasons[si].episodes.indexOfFirst { it.number == e }
        return ei < seasons[si].episodes.size - 1 || si < seasons.size - 1
    }

    fun hasPreviousEpisode(): Boolean {
        val seasons = context.seasons ?: return false
        val s = context.season ?: return false
        val e = context.episode ?: return false
        val si = seasons.indexOfFirst { it.number == s }
        if (si == -1) return false
        val ei = seasons[si].episodes.indexOfFirst { it.number == e }
        return ei > 0 || si > 0
    }

    private var dataSourceFactory: OkHttpDataSource.Factory? = null

    fun getDataSourceFactory(): OkHttpDataSource.Factory {
        dataSourceFactory?.let { return it }
        val playerOkHttpClient = okHttpClient.newBuilder()
            .connectTimeout(ua.ukrtv.app.Constants.CONNECT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(ua.ukrtv.app.Constants.READ_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()

                val refererToUse = when {
                    context.referer.isNotEmpty() -> context.referer
                    url.contains("eneyida") || context.pageUrl.contains("eneyida") -> "https://eneyida.tv/"
                    url.contains("ashdi") || url.contains("hdvb") || url.contains("vidmoly") || url.contains("mcloud") -> "https://uakino.best/"
                    else -> "https://uakino.best/"
                }

                val originToUse = try {
                    val uri = java.net.URI(refererToUse)
                    "${uri.scheme}://${uri.host}"
                } catch (e: Exception) { refererToUse.substringBefore("/", refererToUse) }

                chain.proceed(
                    request.newBuilder()
                        .header("User-Agent", ua.ukrtv.app.Constants.USER_AGENT)
                        .header("Referer", refererToUse)
                        .header("Origin", originToUse)
                        .build()
                )
            }
            .build()
        return OkHttpDataSource.Factory(playerOkHttpClient).also { dataSourceFactory = it }
    }

    fun buildPlayer(context: Context, dsFactory: DataSource.Factory): ExoPlayer {
        return playerFactory.buildPlayer(context, dsFactory, _codecPolicy.value)
    }

    fun createMediaSource(state: PlayerUiState.Ready, dsFactory: DataSource.Factory): MediaSource {
        val mediaItem = MediaItem.Builder()
            .setUri(state.url)
            .setMimeType(
                when {
                    state.url.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
                    state.url.contains(".mpd") -> MimeTypes.APPLICATION_MPD
                    else -> null
                }
            )
            .build()
        return DefaultMediaSourceFactory(dsFactory).createMediaSource(mediaItem)
    }

    fun onEpisodeSelected(s: Int, e: Int) {
        context.season = s
        context.episode = e
        context.episodeId = "s${s}e${e}"
        context.retryCount = 0
        loadStream(context.pageUrl, "S$s E$e")
    }

    fun onPlayerProgressUpdate(p: Long, d: Long) {
        _currentPosition.value = p
        _duration.value = d
    }

    fun updateAvailableTracks(tracks: Tracks) {
        val videoTracks = mutableListOf<TrackInfo>()
        val audioTracks = mutableListOf<TrackInfo>()
        val subtitleTracks = mutableListOf<TrackInfo>()

        for (groupIdx in tracks.groups.indices) {
            val group = tracks.groups[groupIdx]
            val dest = when (group.type) {
                C.TRACK_TYPE_VIDEO -> videoTracks
                C.TRACK_TYPE_AUDIO -> audioTracks
                C.TRACK_TYPE_TEXT -> subtitleTracks
                else -> continue
            }
            for (trackIdx in 0 until group.length) {
                val format = group.getTrackFormat(trackIdx)
                val label = buildString {
                    when (group.type) {
                        C.TRACK_TYPE_VIDEO -> {
                            if (format.height > 0) append("${format.height}p")
                            else if (format.bitrate > 0) append("${format.bitrate / 1000}kbps")
                            else append("Track $trackIdx")
                            if (format.frameRate > 0) append(" ${format.frameRate}fps")
                            if (format.codecs != null) append(" (${format.codecs})")
                        }
                        C.TRACK_TYPE_AUDIO -> {
                            val lang = format.language
                            if (!lang.isNullOrBlank()) append(lang.uppercase())
                            if (format.channelCount > 0) {
                                if (!lang.isNullOrBlank()) append(" ")
                                append("${format.channelCount}.0")
                            }
                            if (lang.isNullOrBlank() && format.channelCount <= 0) append("Track $trackIdx")
                            if (format.bitrate > 0) append(" ${format.bitrate / 1000}kbps")
                        }
                        C.TRACK_TYPE_TEXT -> {
                            val lang = format.language
                            if (!lang.isNullOrBlank()) append(lang.uppercase())
                            else append("Subtitle $trackIdx")
                            if (format.label != null) append(" (${format.label})")
                        }
                    }
                }
                dest.add(TrackInfo(groupIdx, trackIdx, label, group.type))
            }
        }

        _availableTracks.value = videoTracks
        _availableAudioTracks.value = audioTracks
        _selectedAudioTrackIndex.value = audioTracks.firstOrNull { trackIdxMatches(it, tracks) }?.trackIndex
        _availableSubtitleTracks.value = subtitleTracks
        _selectedSubtitleTrackIndex.value = subtitleTracks.firstOrNull { trackIdxMatches(it, tracks) }?.trackIndex
    }

    private fun trackIdxMatches(trackInfo: TrackInfo, tracks: Tracks): Boolean {
        val group = tracks.groups.getOrNull(trackInfo.groupIndex) ?: return false
        return group.isTrackSupported(trackInfo.trackIndex)
    }

    fun selectTrack(trackInfo: TrackInfo, player: ExoPlayer) {
        val tracks = player.currentTracks
        val group = tracks.groups.getOrNull(trackInfo.groupIndex) ?: return

        val override = TrackSelectionOverride(
            group.mediaTrackGroup,
            listOf(trackInfo.trackIndex)
        )
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .addOverride(override)
            .build()
        when (trackInfo.type) {
            C.TRACK_TYPE_VIDEO -> _selectedTrackIndex.value = trackInfo.trackIndex
            C.TRACK_TYPE_AUDIO -> _selectedAudioTrackIndex.value = trackInfo.trackIndex
            C.TRACK_TYPE_TEXT -> _selectedSubtitleTrackIndex.value = trackInfo.trackIndex
        }
    }

    fun clearTrackOverride(player: ExoPlayer) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverrides()
            .build()
        _selectedTrackIndex.value = null
        _selectedAudioTrackIndex.value = null
        _selectedSubtitleTrackIndex.value = null
    }
}
