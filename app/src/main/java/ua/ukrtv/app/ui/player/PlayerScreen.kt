package ua.ukrtv.app.ui.player

import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.view.KeyEvent
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun PlayerScreen(
    url: String,
    contentId: String,
    title: String,
    poster: String = "",
    season: Int? = null,
    episode: Int? = null,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val dataSourceFactory = remember { viewModel.getDataSourceFactory() }
    val recreateSignal by viewModel.recreateSignal.collectAsState()

    val player = remember(recreateSignal) {
        viewModel.buildPlayer(context, dataSourceFactory)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            private var loudnessEnhancer: LoudnessEnhancer? = null

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.setIsPlaying(isPlaying)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    try {
                        loudnessEnhancer?.release()
                        loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                            setTargetGain(0)
                            enabled = true
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerScreen", "LoudnessEnhancer error", e)
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                viewModel.updateAvailableTracks(tracks)
            }

            fun releaseEnhancer() {
                loudnessEnhancer?.release()
                loudnessEnhancer = null
            }
        }

        player.addListener(listener)
        viewModel.startTracking(player)

        onDispose {
            viewModel.saveProgress(player.currentPosition, player.duration)
            viewModel.stopTracking()
            player.removeListener(listener)
            listener.releaseEnhancer()
            player.release()
        }
    }

    LaunchedEffect(url, contentId) {
        viewModel.initialize(
            contentId = contentId,
            title = title,
            pageUrl = url,
            season = season,
            episode = episode,
            poster = poster
        )
    }

    LaunchedEffect(player) {
        while (true) {
            delay(10000)
            if (player.isPlaying && player.duration > 0) {
                viewModel.saveProgress(player.currentPosition, player.duration)
            }
        }
    }

    PlayerContent(
        state = state,
        player = player,
        viewModel = viewModel,
        onBack = onBack,
        title = title,
        season = season,
        episode = episode
    )
}

@UnstableApi
@Composable
private fun PlayerContent(
    state: PlayerUiState,
    player: ExoPlayer,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    title: String,
    season: Int?,
    episode: Int?
) {
    val focusRequester = remember { FocusRequester() }
    val showControls by viewModel.showControls.collectAsState()
    var showQualityMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    val availableTracks by viewModel.availableTracks.collectAsState()
    val selectedTrack by viewModel.selectedTrackIndex.collectAsState()
    val availableAudioTracks by viewModel.availableAudioTracks.collectAsState()
    val selectedAudioTrack by viewModel.selectedAudioTrackIndex.collectAsState()
    val availableSubtitleTracks by viewModel.availableSubtitleTracks.collectAsState()
    val selectedSubtitleTrack by viewModel.selectedSubtitleTrackIndex.collectAsState()
    val codecPolicy by viewModel.codecPolicy.collectAsState()

    val playFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showControls) {
        if (showControls) {
            try { playFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val closeAllMenus: () -> Unit = {
        showQualityMenu = false
        showAudioMenu = false
        showSubtitleMenu = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0D))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { handleKeyEvent(it.nativeKeyEvent, player, viewModel, onBack, showControls, closeAllMenus) }
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        when (val s = state) {
            is PlayerUiState.Idle, is PlayerUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF6E85B7))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "ПЕРЕВІРКА ПРОВАЙДЕРІВ...",
                            color = Color(0xFFE1E1E1).copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            is PlayerUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = Color(0xFFE57373), fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(32.dp))
                        androidx.tv.material3.Button(onClick = { viewModel.retry() }) {
                            Text(stringResource(ua.ukrtv.app.R.string.retry))
                        }
                    }
                }
            }
            is PlayerUiState.Ready -> {
                var endedCountdown by remember { mutableStateOf<Int?>(null) }

                DisposableEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                if (viewModel.navigateToNextEpisode()) {
                                    endedCountdown = 10
                                }
                            }
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            viewModel.onPlayerError(error, player.currentPosition)
                        }
                    }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                        endedCountdown = null
                    }
                }

                LaunchedEffect(endedCountdown) {
                    endedCountdown?.let { countdown ->
                        if (countdown > 0) {
                            delay(1000)
                            endedCountdown = countdown - 1
                        } else {
                            endedCountdown = null
                        }
                    }
                }

                LaunchedEffect(showControls) {
                    if (showControls) {
                        delay(5000)
                        viewModel.setShowControls(false)
                    }
                }

                LaunchedEffect(s.url, s.loadTrigger) {
                    if (s.url.isNotEmpty()) {
                        val mediaItem = MediaItem.Builder()
                            .setUri(s.url)
                            .setMimeType(
                                when {
                                    s.url.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
                                    s.url.contains(".mpd") -> MimeTypes.APPLICATION_MPD
                                    else -> null
                                }
                            )
                            .build()
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        if (s.positionMs > 0) player.seekTo(s.positionMs)
                        player.playWhenReady = true
                    }
                }

                LaunchedEffect(Unit) {
                    delay(3000)
                    viewModel.logAfrEnvironment("after_play_3s")
                    delay(5000)
                    viewModel.logAfrEnvironment("after_play_8s")
                }

                AndroidView(
                    factory = { ctx ->
                        val view = LayoutInflater.from(ctx)
                            .inflate(ua.ukrtv.app.R.layout.player_view_texture, null) as PlayerView
                        view.player = player
                        val videoSurface = view.videoSurfaceView
                        if (videoSurface is android.view.SurfaceView) {
                            videoSurface.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                                    viewModel.setSurface(holder.surface)
                                }
                                override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, w: Int, h: Int) {
                                    viewModel.setSurface(holder.surface)
                                }
                                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                                    viewModel.setSurface(null)
                                }
                            })
                        }
                        view
                    },
                    update = { view -> view.player = player },
                    modifier = Modifier.fillMaxSize()
                )

                val isPlaying by viewModel.isPlaying.collectAsState()

                var currentPosition by remember { mutableStateOf(player.currentPosition) }
                var duration by remember { mutableStateOf(player.duration) }
                var bufferedPosition by remember { mutableStateOf(player.bufferedPosition) }

                LaunchedEffect(isPlaying) {
                    if (isPlaying) {
                        while (true) {
                            currentPosition = player.currentPosition
                            duration = player.duration
                            bufferedPosition = player.bufferedPosition
                            delay(1000)
                        }
                    }
                }

                if (showControls) {
                    PlayerOverlay(
                        title = title,
                        isPlaying = isPlaying,
                        positionMs = currentPosition,
                        durationMs = duration,
                        bufferedPositionMs = bufferedPosition,
                        nextCountdown = endedCountdown,
                        onPlayPauseToggle = { if (player.isPlaying) player.pause() else player.play() },
                        onSeekBackward = { player.seekTo(maxOf(0L, player.currentPosition - 10_000)) },
                        onSeekForward = { player.seekTo(player.currentPosition + 10_000) },
                        onSeek = { ratio -> player.seekTo((ratio * player.duration).toLong()) },
                        onShowAudioMenu = { showAudioMenu = !showAudioMenu },
                        onShowSubtitleMenu = { showSubtitleMenu = !showSubtitleMenu },
                        onShowQualityMenu = { showQualityMenu = !showQualityMenu },
                        onPreviousEpisode = { viewModel.navigateToPreviousEpisode() },
                        onNextEpisode = { viewModel.navigateToNextEpisode() },
                        hasPreviousEpisode = viewModel.hasPreviousEpisode(),
                        hasNextEpisode = viewModel.hasNextEpisode(),
                        onToggleCodec = { viewModel.toggleCodecPolicy() },
                        codecPolicy = codecPolicy,
                        season = viewModel.playerContext.season,
                        episode = viewModel.playerContext.episode,
                        playFocusRequester = playFocusRequester,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (showQualityMenu && availableTracks.isNotEmpty()) {
                        TrackSelectorMenu(
                            title = stringResource(ua.ukrtv.app.R.string.quality_video),
                            tracks = availableTracks,
                            selectedTrackIndex = selectedTrack,
                            onTrackSelected = { track ->
                                viewModel.selectTrack(track, player)
                                showQualityMenu = false
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
                                viewModel.selectTrack(track, player)
                                showAudioMenu = false
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
                                viewModel.selectTrack(track, player)
                                showSubtitleMenu = false
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
            is PlayerUiState.SeriesSelection -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ua.ukrtv.app.ui.detail.SeasonEpisodePicker(
                        seasons = s.seasons,
                        onEpisodeClick = { season, ep -> viewModel.onEpisodeSelected(season, ep.number) }
                    )
                }
            }
        }
    }
}

private fun handleKeyEvent(
    keyEvent: KeyEvent,
    player: ExoPlayer,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    showControls: Boolean,
    closeAllMenus: () -> Unit
): Boolean {
    if (keyEvent.action != KeyEvent.ACTION_DOWN) return false

    if (!showControls) {
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.setShowControls(true)
                return true
            }
        }
    }

    when (keyEvent.keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            if (showControls) return false
            player.seekTo(maxOf(0L, player.currentPosition - 10_000))
            return true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (showControls) return false
            player.seekTo(player.currentPosition + 10_000)
            return true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            if (showControls) return false
            viewModel.setShowControls(true)
            return true
        }
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
            if (player.isPlaying) player.pause() else player.play()
            return true
        }
        KeyEvent.KEYCODE_BACK -> {
            closeAllMenus()
            if (showControls) {
                viewModel.setShowControls(false)
                return true
            }
            onBack()
            return true
        }
    }
    return false
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackSelectorMenu(
    title: String,
    tracks: List<TrackInfo>,
    selectedTrackIndex: Int?,
    onTrackSelected: (TrackInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(end = 32.dp)
            .background(
                Color(0xE6151517),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = Color(0xFFE1E1E1),
                fontSize = 16.sp
            )
            Spacer(Modifier.height(12.dp))
            tracks.forEach { track ->
                val isSelected = track.trackIndex == selectedTrackIndex
                androidx.tv.material3.Button(
                    onClick = { onTrackSelected(track) }
                ) {
                    Text(
                        text = track.label,
                        color = if (isSelected) Color(0xFF6E85B7) else Color(0xFFE1E1E1),
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
