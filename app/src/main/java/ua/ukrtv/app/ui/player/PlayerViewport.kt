package ua.ukrtv.app.ui.player

import android.view.SurfaceView
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import ua.ukrtv.app.ui.player.StatsOverlay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import ua.ukrtv.app.ui.theme.BrandBlue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.util.AppLogger

@UnstableApi
@Composable
fun PlayerReadyContent(
    state: PlayerStatus.Ready,
    playerState: PlayerState,
    player: ExoPlayer,
    viewModel: PlayerViewModel,
    title: String,
    videoResizeMode: Int,
    isMuted: Boolean,
    isChildMode: Boolean,
    showStats: Boolean,
    hasEpisodes: Boolean,
    playFocusRequester: FocusRequester,
    playButtonFocusRequester: FocusRequester,
    onNavigateToSeasons: (() -> Unit)? = null,
    showControls: Boolean,
    showQualityMenu: Boolean,
    showAudioMenu: Boolean,
    showSubtitleMenu: Boolean,
    showScaleMenu: Boolean,
    availableTracks: List<TrackInfo>,
    selectedTrack: Int?,
    availableAudioTracks: List<TrackInfo>,
    selectedAudioTrack: Int?,
    availableSubtitleTracks: List<TrackInfo>,
    selectedSubtitleTrack: Int?,
    brandColor: Color = BrandBlue,
    showQualityMenuChange: (Boolean) -> Unit,
    showAudioMenuChange: (Boolean) -> Unit,
    showSubtitleMenuChange: (Boolean) -> Unit,
    showScaleMenuChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit
) {
    var endedCountdown by rememberSaveable { mutableStateOf<Int?>(null) }
    var countdownEpisode by rememberSaveable { mutableStateOf<Episode?>(null) }

    fun resolveCountdownEpisode(): Episode? {
        val seasons = playerState.availableSeasons ?: return null
        val sNum = playerState.currentSeason ?: return null
        val eNum = playerState.currentEpisode ?: return null
        return seasons.find { it.number == sNum }
            ?.episodes?.find { it.number == eNum }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView<SurfaceView>(
            factory = { ctx ->
                SurfaceView(ctx).also { surfaceView ->
                    surfaceView.keepScreenOn = true
                    player.setVideoSurfaceView(surfaceView)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        LaunchedEffect(videoResizeMode) {
            player.setVideoScalingMode(
                if (videoResizeMode == 0) C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                else C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            )
        }

        LaunchedEffect(state.url, state.loadTrigger) {
            if (state.url.isNotEmpty()) {
                AppLogger.d("PlayerScreen", "Preparing: ${state.url.take(30)}")
                val mediaItem = MediaItem.Builder()
                    .setUri(state.url)
                    .setMimeType(
                        when (state.streamType) {
                            StreamType.HLS -> MimeTypes.APPLICATION_M3U8
                            StreamType.MPD -> MimeTypes.APPLICATION_MPD
                            StreamType.MP4 -> MimeTypes.VIDEO_MP4
                            else -> null
                        }
                    )
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()
                if (state.positionMs > 0) player.seekTo(state.positionMs)
                player.playWhenReady = true
            }
        }

        val isPlaying = player.isPlaying

        var currentPosition by remember(player) { mutableStateOf(player.currentPosition) }
        var duration by remember(player) { mutableStateOf(player.duration) }
        var bufferedPosition by remember(player) { mutableStateOf(player.bufferedPosition) }

        DisposableEffect(player) {
            val playbackListener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        if (viewModel.prepareNextEpisode()) {
                            endedCountdown = NEXT_EPISODE_COUNTDOWN_SEC
                        }
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    viewModel.onPlayerError(error)
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        currentPosition = player.currentPosition
                        duration = player.duration
                        bufferedPosition = player.bufferedPosition
                    }
                }
            }
            player.addListener(playbackListener)
            onDispose {
                player.removeListener(playbackListener)
                endedCountdown = null
            }
        }

        LaunchedEffect(isPlaying) {
            if (isPlaying) {
                while (true) {
                    delay(1000)
                    currentPosition = player.currentPosition
                    duration = player.duration
                    bufferedPosition = player.bufferedPosition
                    viewModel.onIntent(PlayerIntent.UpdateProgress(currentPosition, duration))
                }
            }
        }

        val showSkipIntro by remember {
            derivedStateOf { isPlaying && currentPosition in 0..SKIP_INTRO_WINDOW_MS }
        }

        PlayerOverlay(
            visible = showControls,
            brandColor = brandColor,
            title = title,
            isPlaying = isPlaying,
            isMuted = isMuted,
            positionMs = currentPosition,
            durationMs = duration,
            bufferedPositionMs = bufferedPosition,
            showSkipIntro = showSkipIntro,
            onSkipIntro = { player.seekTo(player.currentPosition + SKIP_INTRO_STEP_MS) },
            onToggleMute = { viewModel.onIntent(PlayerIntent.ToggleMute) },
            isChildMode = isChildMode,
            onToggleChildMode = { viewModel.onIntent(PlayerIntent.ToggleChildMode) },
            nextCountdown = endedCountdown,
            countdownEpisode = countdownEpisode,
            countdownSeason = playerState.currentSeason,
            onPlayPauseToggle = { viewModel.onIntent(PlayerIntent.TogglePlay) },
            onSeekBackward = { player.seekTo(maxOf(0L, player.currentPosition - SEEK_STEP_MS)) },
            onSeekForward = { player.seekTo(player.currentPosition + SEEK_STEP_MS) },
            onSeek = { ratio -> player.seekTo((ratio * player.duration).toLong()) },
            onShowAudioMenu = { showAudioMenuChange(!showAudioMenu) },
            onShowSubtitleMenu = { showSubtitleMenuChange(!showSubtitleMenu) },
            onShowQualityMenu = { showQualityMenuChange(!showQualityMenu) },
            onShowScaleMenu = { showScaleMenuChange(!showScaleMenu) },
            onShowStats = { viewModel.onIntent(PlayerIntent.ToggleStats) },
            onPreviousEpisode = { viewModel.onIntent(PlayerIntent.NavigatePrevious) },
            onNextEpisode = { viewModel.onIntent(PlayerIntent.NavigateNext) },
            hasPreviousEpisode = viewModel.hasPreviousEpisode(),
            hasNextEpisode = viewModel.hasNextEpisode(),
            hasEpisodes = hasEpisodes,
            onShowEpisodes = { onNavigateToSeasons?.invoke() },
            season = playerState.currentSeason,
            episode = playerState.currentEpisode,
            playFocusRequester = playButtonFocusRequester,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(300, easing = LinearEasing)),
            exit = fadeOut(tween(300, easing = LinearEasing))
        ) {
            Box {
                if (showQualityMenu && availableTracks.isNotEmpty()) {
                    TrackSelectorMenu(
                        title = stringResource(ua.ukrtv.app.R.string.quality_video),
                        tracks = availableTracks,
                        selectedTrackIndex = selectedTrack,
                        onTrackSelected = { track ->
                            viewModel.trackManager.selectTrack(track, player)
                            showQualityMenuChange(false)
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }

                if (showAudioMenu && availableAudioTracks.isNotEmpty()) {
                    TrackSelectorMenu(
                        title = "Аудіо",
                        tracks = availableAudioTracks,
                        selectedTrackIndex = selectedAudioTrack,
                        onTrackSelected = { track ->
                            viewModel.trackManager.selectTrack(track, player)
                            showAudioMenuChange(false)
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }

                if (showSubtitleMenu && availableSubtitleTracks.isNotEmpty()) {
                    TrackSelectorMenu(
                        title = "Субтитри",
                        tracks = availableSubtitleTracks,
                        selectedTrackIndex = selectedSubtitleTrack,
                        onTrackSelected = { track ->
                            viewModel.trackManager.selectTrack(track, player)
                            showSubtitleMenuChange(false)
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }

                if (showScaleMenu) {
                    val scaleOptions = listOf(
                        TrackInfo(0, 0, "Вписати (з чорними смугами)"),   // FIT
                        TrackInfo(0, 1, "Весь екран (обрізати краї)"),   // ZOOM
                        TrackInfo(0, 2, "Розтягнути (без пропорцій)")    // FILL
                    )
                    TrackSelectorMenu(
                        title = "Масштаб відео",
                        tracks = scaleOptions,
                        selectedTrackIndex = videoResizeMode,
                        onTrackSelected = { track ->
                            viewModel.onIntent(PlayerIntent.ChangeResizeMode(track.trackIndex))
                            showScaleMenuChange(false)
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }

            }
        }

        if (showStats) {
            StatsOverlay(
                player = player,
                modifier = Modifier.align(Alignment.TopEnd)
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

