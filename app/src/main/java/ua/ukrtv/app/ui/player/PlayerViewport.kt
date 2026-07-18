package ua.ukrtv.app.ui.player

import android.view.SurfaceView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import ua.ukrtv.app.ui.theme.BrandBlue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.player.PlaybackEngine
import ua.ukrtv.app.util.AppLogger

@UnstableApi
@Composable
fun PlayerReadyContent(
    status: PlayerStatus,
    playerState: PlayerState,
    engine: PlaybackEngine?,
    viewModel: PlayerViewModel,
    title: String,
    scaleMode: ScaleMode,
    hasEpisodes: Boolean,
    playFocusRequester: FocusRequester,
    playButtonFocusRequester: FocusRequester,
    isShowingControls: Boolean,
    brandColor: Color = BrandBlue,
    heldSeekDir: SeekDirection? = null,
    showOverlay: Boolean = true,
    onSeek: (Long) -> Unit
) {
    var endedCountdown by remember { mutableStateOf<Int?>(null) }
    var countdownEpisode by remember { mutableStateOf<Episode?>(null) }

    fun resolveCountdownEpisode(): Episode? {
        val seasons = playerState.availableSeasons ?: return null
        val sNum = playerState.currentSeason ?: return null
        val eNum = playerState.currentEpisode ?: return null
        return seasons.find { it.number == sNum }
            ?.episodes?.find { it.number == eNum }
    }

    var videoWidth by remember { mutableStateOf(engine?.getVideoWidth() ?: 0) }
    var videoHeight by remember { mutableStateOf(engine?.getVideoHeight() ?: 0) }
    val readyStatus = status as? PlayerStatus.Ready

    val zoomScale = remember(videoWidth, videoHeight) {
        if (videoWidth <= 0) 1.33f else {
            val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
            val displayRatio = 16f / 9f
            if (videoRatio > displayRatio + 0.01f) videoRatio / displayRatio else 1.33f
        }
    }
    val needsManualScale = remember(videoWidth, videoHeight) {
        videoWidth > 0 && videoHeight > 0 && videoWidth.toFloat() / videoHeight.toFloat() <= 16f / 9f + 0.01f
    }

    var surfaceRef by remember { mutableStateOf<SurfaceView?>(null) }
    var surfaceAttached by remember { mutableStateOf(false) }
    var appliedScaleMode by remember { mutableStateOf<ScaleMode?>(null) }
    var appliedZoomFactor by remember { mutableStateOf(0f) }
    val surfaceModifier = remember(scaleMode, videoWidth, videoHeight) {
        if (engine?.supportsNativeScaling == true) {
            Modifier.fillMaxSize()
        } else if (scaleMode == ScaleMode.FIT) {
            val ratio = if (videoWidth > 0 && videoHeight > 0) {
                videoWidth.toFloat() / videoHeight.toFloat()
            } else 16f / 9f
            Modifier.aspectRatio(ratio)
        } else {
            Modifier.fillMaxSize()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView<SurfaceView>(
            factory = { ctx ->
                SurfaceView(ctx).also { surfaceView ->
                    surfaceView.keepScreenOn = true
                    surfaceView.setZOrderMediaOverlay(true)
                }
            },
            update = { surfaceView ->
                if (engine == null) return@AndroidView
                if (surfaceRef !== surfaceView) {
                    surfaceRef = surfaceView
                    surfaceAttached = false
                }
                if (!surfaceAttached && readyStatus != null) {
                    AppLogger.d("PlayerViewport", "setSurface called (engine=${engine::class.simpleName})")
                    engine.setSurface(surfaceView)
                    surfaceAttached = true
                    engine.setVideoScalingMode(scaleMode.ordinal)
                }
                if (engine.supportsNativeScaling) {
                    if (appliedScaleMode != scaleMode) {
                        engine.setVideoScalingMode(scaleMode.ordinal)
                        appliedScaleMode = scaleMode
                    }
                } else {
                    val newFactor = if (scaleMode == ScaleMode.ZOOM && needsManualScale) zoomScale else 1.0f
                    if (appliedScaleMode != scaleMode || appliedZoomFactor != newFactor) {
                        surfaceView.scaleX = newFactor
                        surfaceView.scaleY = newFactor
                        surfaceView.pivotX = (surfaceView.width / 2f).coerceAtLeast(0f)
                        surfaceView.pivotY = (surfaceView.height / 2f).coerceAtLeast(0f)
                        appliedScaleMode = scaleMode
                        appliedZoomFactor = newFactor
                    }
                }
            },
            modifier = surfaceModifier
        )

        val isPlaying = engine?.isPlaying ?: false

        var currentPosition by remember(engine) { mutableStateOf(engine?.currentPosition ?: 0L) }
        var duration by remember(engine) { mutableStateOf(engine?.duration ?: 0L) }

        DisposableEffect(engine) {
            if (engine == null) return@DisposableEffect onDispose {}
            val playbackListener = object : PlaybackEngine.EngineListener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    viewModel.updateIsPlaying(isPlaying)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    val mappedState = when (state) {
                        PlaybackEngine.STATE_READY -> Player.STATE_READY
                        PlaybackEngine.STATE_BUFFERING -> Player.STATE_BUFFERING
                        PlaybackEngine.STATE_ENDED -> Player.STATE_ENDED
                        PlaybackEngine.STATE_IDLE -> Player.STATE_IDLE
                        PlaybackEngine.STATE_PLAYING -> Player.STATE_READY
                        PlaybackEngine.STATE_PAUSED -> Player.STATE_READY
                        else -> Player.STATE_IDLE
                    }
                    viewModel.updatePlaybackState(mappedState)
                }

                override fun onPositionChanged(positionMs: Long) {
                    currentPosition = positionMs
                }

                override fun onLengthChanged(lengthMs: Long) {
                    duration = lengthMs
                }

                override fun onError(message: String) {
                    viewModel.onEngineError(message)
                }

                override fun onError(error: PlaybackException) {
                    viewModel.onPlayerError(error)
                }

                override fun onVideoSizeChanged(width: Int, height: Int) {
                    videoWidth = width
                    videoHeight = height
                    viewModel.updateCodecInfo("${width}×${height}", emptyList())
                }

                override fun onCodecInfoChanged(codecName: String, width: Int, height: Int) {
                    videoWidth = width
                    videoHeight = height
                    val display = if (codecName.isNotBlank()) "$codecName ${width}×${height}" else "${width}×${height}"
                    viewModel.updateCodecInfo(display, emptyList())
                }

                override fun onEndReached() {
                    viewModel.saveProgress(currentPosition, duration)
                    if (viewModel.prepareNextEpisode()) {
                        endedCountdown = NEXT_EPISODE_COUNTDOWN_SEC
                    }
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    viewModel.trackManager.updateAvailableTracks(tracks)
                }
            }
            engine.addListener(playbackListener)
            onDispose {
                engine.removeListener(playbackListener)
            }
        }

        LaunchedEffect(isPlaying) {
            if (isPlaying) {
                var saveCounter = 0
                while (true) {
                    delay(1000)
                    currentPosition = engine.currentPosition
                    duration = engine.duration
                    saveCounter++
                    if (saveCounter >= 10) {
                        saveCounter = 0
                        viewModel.saveProgress(currentPosition, duration)
                    }
                }
            }
        }

        if (readyStatus != null && engine != null) {
            DisposableEffect(readyStatus.loadTrigger) {
                onDispose {
                    endedCountdown = null
                }
            }

            LaunchedEffect(readyStatus.url, readyStatus.loadTrigger) {
                if (readyStatus.url.isNotEmpty()) {
                    AppLogger.d("PlayerScreen", "Preparing: ${readyStatus.url.take(30)} (engine=${engine::class.simpleName})")
                    engine.setMedia(readyStatus.url, readyStatus.positionMs, readyStatus.referer)
                }
            }

            val showSkipIntro by remember {
                derivedStateOf { isPlaying && currentPosition in 0..SKIP_INTRO_WINDOW_MS }
            }

            val allEpisodesAreOne = remember(playerState.availableSeasons) {
                playerState.availableSeasons?.all { season ->
                    season.episodes.all { it.number <= 1 }
                } == true
            }

            if (showOverlay) {
                PlayerOverlay(
                    visible = isShowingControls,
                    brandColor = brandColor,
                    title = title,
                    isPlaying = isPlaying,
                    positionMs = currentPosition,
                    durationMs = duration,
                    bufferedPositionMs = duration,
                    showSkipIntro = showSkipIntro,
                    onSkipIntro = { engine.seekTo(currentPosition + SKIP_INTRO_STEP_MS) },
                    nextCountdown = endedCountdown,
                    countdownEpisode = countdownEpisode,
                    countdownSeason = playerState.currentSeason,
                    onPlayPauseToggle = { viewModel.togglePlay() },
                    onSeekBackward = { engine.seekTo(maxOf(0L, currentPosition - SEEK_STEP_MS)) },
                    onSeekForward = { engine.seekTo(currentPosition + SEEK_STEP_MS) },
                    onSeek = { ratio -> engine.seekTo((ratio * duration).toLong()) },
                    hasEpisodes = hasEpisodes,
                    hasNextEpisode = viewModel.hasNextEpisode(),
                    onNextEpisode = {
                        viewModel.saveProgress(currentPosition, duration)
                        viewModel.navigateToNextEpisode()
                    },
                    heldSeekDir = heldSeekDir,
                    season = playerState.currentSeason,
                    episode = playerState.currentEpisode,
                    showSeasonEpisode = !allEpisodesAreOne,
                    playFocusRequester = playButtonFocusRequester,
                    pickerColumns = playerState.pickerColumns,
                    pickerFocusedIndex = playerState.pickerFocusedIndex,
                    onPickerColumnFocused = { viewModel.onPickerColumnFocused(it) },
                    onPickerValueChange = { viewModel.onPickerValueChange(it) },
                    onPickerCommit = { viewModel.onPickerCommit() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            LaunchedEffect(endedCountdown) {
                endedCountdown?.let { countdown ->
                    if (countdown > 0) {
                        delay(1000)
                        endedCountdown = countdown - 1
                    } else {
                        val ep = resolveCountdownEpisode()
                        countdownEpisode = ep
                        endedCountdown = null
                        viewModel.executePreparedNavigation()
                    }
                }
            }
        }
    }
}
