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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
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
    status: PlayerStatus,
    playerState: PlayerState,
    player: ExoPlayer,
    viewModel: PlayerViewModel,
    title: String,
    scaleMode: ScaleMode,
    hasEpisodes: Boolean,
    playFocusRequester: FocusRequester,
    playButtonFocusRequester: FocusRequester,
    isShowingControls: Boolean,
    brandColor: Color = BrandBlue,
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

    var videoSize by remember { mutableStateOf(player.videoSize) }
    val readyStatus = status as? PlayerStatus.Ready

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView<SurfaceView>(
            factory = { ctx ->
                SurfaceView(ctx).also { surfaceView ->
                    surfaceView.keepScreenOn = true
                }
            },
            update = { surfaceView ->
                if (readyStatus != null) {
                    player.setVideoSurfaceView(surfaceView)
                }
                player.setVideoScalingMode(
                    when (scaleMode) {
                        ScaleMode.FIT -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                        ScaleMode.ZOOM -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    }
                )
            },
            modifier = if (scaleMode == ScaleMode.FIT) {
                val ratio = if (videoSize.width > 0 && videoSize.height > 0) {
                    (videoSize.width.toFloat() / videoSize.height.toFloat()) * videoSize.pixelWidthHeightRatio
                } else 16f / 9f
                Modifier.aspectRatio(ratio)
            } else {
                Modifier.fillMaxSize()
            }
        )

        val isPlaying = player.isPlaying

        var currentPosition by remember(player) { mutableStateOf(player.currentPosition) }
        var duration by remember(player) { mutableStateOf(player.duration) }
        var bufferedPosition by remember(player) { mutableStateOf(player.bufferedPosition) }

        DisposableEffect(player) {
            val playbackListener = object : Player.Listener {
                override fun onVideoSizeChanged(newVideoSize: VideoSize) {
                    videoSize = newVideoSize
                    val fmt = player.videoFormat
                    val codecName = fmt?.codecs?.substringBefore(".")?.takeIf { it.isNotBlank() }
                        ?: fmt?.codecs?.takeIf { it.isNotBlank() }
                    val display = buildString {
                        codecName?.let { append(it) }
                        if (newVideoSize.width > 0 && newVideoSize.height > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${newVideoSize.width}×${newVideoSize.height}")
                        }
                    }
                    viewModel.updateCodecInfo(display, emptyList())
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    viewModel.updatePlaybackState(playbackState)
                    if (playbackState == Player.STATE_READY) {
                        viewModel.trackManager.updateAvailableTracks(player.currentTracks)
                        val fmt = player.videoFormat
                        val vs = player.videoSize
                        val codecName = fmt?.codecs?.substringBefore(".")?.takeIf { it.isNotBlank() }
                            ?: fmt?.codecs?.takeIf { it.isNotBlank() }
                        val display = buildString {
                            codecName?.let { append(it) }
                            if (vs.width > 0 && vs.height > 0) {
                                if (isNotEmpty()) append(" • ")
                                append("${vs.width}×${vs.height}")
                            }
                        }
                        viewModel.updateCodecInfo(display, extractCodecs(player.currentTracks))
                    }
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
                    viewModel.updateIsPlaying(isPlaying)
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
            }
        }

        LaunchedEffect(isPlaying) {
            if (isPlaying) {
                while (true) {
                    delay(1000)
                    currentPosition = player.currentPosition
                    duration = player.duration
                    bufferedPosition = player.bufferedPosition
                    viewModel.updateProgress(currentPosition, duration)
                }
            }
        }

        if (readyStatus != null) {
            DisposableEffect(readyStatus.loadTrigger) {
                onDispose {
                    endedCountdown = null
                }
            }

            LaunchedEffect(readyStatus.url, readyStatus.loadTrigger) {
                if (readyStatus.url.isNotEmpty()) {
                    AppLogger.d("PlayerScreen", "Preparing: ${readyStatus.url.take(30)}")
                    val mediaItem = MediaItem.Builder()
                        .setUri(readyStatus.url)
                        .setMimeType(
                            when (readyStatus.streamType) {
                                StreamType.HLS -> MimeTypes.APPLICATION_M3U8
                                StreamType.MPD -> MimeTypes.APPLICATION_MPD
                                StreamType.MP4 -> MimeTypes.VIDEO_MP4
                                else -> null
                            }
                        )
                        .build()
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    if (readyStatus.positionMs > 0) player.seekTo(readyStatus.positionMs)
                    player.playWhenReady = true
                }
            }

            val showSkipIntro by remember {
                derivedStateOf { isPlaying && currentPosition in 0..SKIP_INTRO_WINDOW_MS }
            }

            PlayerOverlay(
                visible = isShowingControls,
                brandColor = brandColor,
                title = title,
                isPlaying = isPlaying,
                positionMs = currentPosition,
                durationMs = duration,
                bufferedPositionMs = bufferedPosition,
                showSkipIntro = showSkipIntro,
                onSkipIntro = { player.seekTo(player.currentPosition + SKIP_INTRO_STEP_MS) },
                nextCountdown = endedCountdown,
                countdownEpisode = countdownEpisode,
                countdownSeason = playerState.currentSeason,
                onPlayPauseToggle = { viewModel.togglePlay() },
                onSeekBackward = { player.seekTo(maxOf(0L, player.currentPosition - SEEK_STEP_MS)) },
                onSeekForward = { player.seekTo(player.currentPosition + SEEK_STEP_MS) },
                onSeek = { ratio -> player.seekTo((ratio * player.duration).toLong()) },
                hasEpisodes = hasEpisodes,
                season = playerState.currentSeason,
                episode = playerState.currentEpisode,
                playFocusRequester = playButtonFocusRequester,
                pickerColumns = playerState.pickerColumns,
                pickerFocusedIndex = playerState.pickerFocusedIndex,
                onPickerColumnFocused = { viewModel.onPickerColumnFocused(it) },
                onPickerValueChange = { viewModel.onPickerValueChange(it) },
                onPickerCommit = { viewModel.onPickerCommit() },
                modifier = Modifier.fillMaxSize()
            )

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

private fun extractCodecs(tracks: Tracks): List<CodecInfo> {
    val mimeTypes = mutableSetOf<String>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        for (i in 0 until group.length) {
            val mime = group.getTrackFormat(i).sampleMimeType ?: continue
            if (mime.startsWith("video/")) mimeTypes.add(mime)
        }
    }
    return mimeTypes.mapNotNull { mime ->
        val name = when {
            mime.contains("avc") || mime.contains("h264") -> "AVC"
            mime.contains("hevc") || mime.contains("h265") -> "HEVC"
            mime.contains("vp9") -> "VP9"
            mime.contains("av01") || mime.contains("av1") -> "AV1"
            else -> return@mapNotNull null
        }
        CodecInfo(mime, name)
    }
}
