package ua.ukrtv.app.ui.player

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import ua.ukrtv.app.ui.theme.BrandBlue
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import ua.ukrtv.app.util.AppLogger


internal const val SEEK_STEP_MS = 10_000L
private const val CONTROLS_HIDE_DELAY_MS = 5_000L
internal const val SKIP_INTRO_WINDOW_MS = 120_000L
internal const val SKIP_INTRO_STEP_MS = 90_000L
internal const val NEXT_EPISODE_COUNTDOWN_SEC = 10

@UnstableApi
@Composable
fun PlayerScreen(
    url: String,
    contentId: String,
    title: String,
    poster: String = "",
    season: Int? = null,
    episode: Int? = null,
    brandColor: Color = BrandBlue,
    onBack: () -> Unit,
    onNavigateToSeasons: (() -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val dataSourceFactory = remember { viewModel.getDataSourceFactory() }
    val player = remember { viewModel.getOrCreatePlayer(context, dataSourceFactory) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.onIntent(PlayerIntent.UpdateIsPlaying(isPlaying))
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                viewModel.onIntent(PlayerIntent.UpdatePlaybackState(playbackState))
                if (playbackState == Player.STATE_READY) {
                    viewModel.trackManager.updateAvailableTracks(player.currentTracks)
                }
            }
        }

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                player.playWhenReady = false
                player.pause()
            }
        }

        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            player.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.saveProgress(player.currentPosition, player.duration)
            viewModel.releasePlayer(player)
        }
    }

    LaunchedEffect(url, contentId, season, episode) {
        viewModel.onIntent(PlayerIntent.Initialize(contentId, title, url, season, episode, poster))
    }

    PlayerContent(
        state = state,
        player = player,
        viewModel = viewModel,
        brandColor = brandColor,
        onBack = onBack,
        onNavigateToSeasons = onNavigateToSeasons,
        title = title
    )
}

@UnstableApi
@Composable
private fun PlayerContent(
    state: PlayerState,
    player: ExoPlayer,
    viewModel: PlayerViewModel,
    brandColor: Color = BrandBlue,
    onBack: () -> Unit,
    onNavigateToSeasons: (() -> Unit)? = null,
    title: String
) {
    val playFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }

    var showQualityMenu by rememberSaveable { mutableStateOf(false) }
    var showAudioMenu by rememberSaveable { mutableStateOf(false) }
    var showSubtitleMenu by rememberSaveable { mutableStateOf(false) }
    var showScaleMenu by rememberSaveable { mutableStateOf(false) }

    val availableTracks by viewModel.trackManager.availableTracks.collectAsState()
    val selectedTrack by viewModel.trackManager.selectedTrackIndex.collectAsState()
    val availableAudioTracks by viewModel.trackManager.availableAudioTracks.collectAsState()
    val selectedAudioTrack by viewModel.trackManager.selectedAudioTrackIndex.collectAsState()
    val availableSubtitleTracks by viewModel.trackManager.availableSubtitleTracks.collectAsState()
    val selectedSubtitleTrack by viewModel.trackManager.selectedSubtitleTrackIndex.collectAsState()

    LaunchedEffect(state.showControls, state.status) {
        if (state.showControls && state.status is PlayerStatus.Ready) {
            delay(150)
            try {
                playButtonFocusRequester.requestFocus()
                AppLogger.d("PlayerScreen", "Focus requested successfully")
            } catch (e: Exception) {
                AppLogger.w("PlayerScreen", "Focus request failed: ${e.message}")
            }
        }
    }

    LaunchedEffect(state.showControls, showQualityMenu, showAudioMenu, showSubtitleMenu, showScaleMenu) {
        if (state.showControls) {
            val anyMenuOpen = showQualityMenu || showAudioMenu || showSubtitleMenu || showScaleMenu
            if (!anyMenuOpen) {
                delay(CONTROLS_HIDE_DELAY_MS)
                viewModel.onIntent(PlayerIntent.SetShowControls(false))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(playFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                val ke = event.nativeKeyEvent
                if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                
                when (ke.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (state.showControls) return@onKeyEvent false
                        viewModel.onIntent(PlayerIntent.SeekTo(maxOf(0L, player.currentPosition - SEEK_STEP_MS)))
                        return@onKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (state.showControls) return@onKeyEvent false
                        viewModel.onIntent(PlayerIntent.SeekTo(player.currentPosition + SEEK_STEP_MS))
                        return@onKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                        if (state.showControls) return@onKeyEvent false
                        viewModel.onIntent(PlayerIntent.SetShowControls(true))
                        return@onKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_BACK -> {
                        if (showQualityMenu || showAudioMenu || showSubtitleMenu || showScaleMenu) {
                            showQualityMenu = false
                            showAudioMenu = false
                            showSubtitleMenu = false
                            showScaleMenu = false
                            return@onKeyEvent true
                        }
                        onBack()
                        return@onKeyEvent true
                    }
                    else -> {}
                }
                false
            }
    ) {
        when (val status = state.status) {
            is PlayerStatus.Loading -> {
                Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF6E85B7))
                }
            }
            is PlayerStatus.Error -> {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(status.message, color = Color.Red, fontSize = 18.sp)
                        androidx.tv.material3.Button(onClick = { viewModel.onIntent(PlayerIntent.Retry) }) {
                            Text("Retry")
                        }
                    }
                }
            }
            is PlayerStatus.Ready -> {
                PlayerReadyContent(
                    state = status,
                    playerState = state,
                    player = player,
                    viewModel = viewModel,
                    title = title,
                    brandColor = brandColor,
                    videoResizeMode = state.videoResizeMode,
                    isMuted = state.isMuted,
                    isChildMode = state.childMode,
                    showStats = state.showStats,
                    hasEpisodes = state.availableSeasons?.isNotEmpty() == true,
                    playFocusRequester = playFocusRequester,
                    playButtonFocusRequester = playButtonFocusRequester,
                    onNavigateToSeasons = onNavigateToSeasons,
                    showControls = state.showControls,
                    showQualityMenu = showQualityMenu,
                    showAudioMenu = showAudioMenu,
                    showSubtitleMenu = showSubtitleMenu,
                    showScaleMenu = showScaleMenu,
                    availableTracks = availableTracks,
                    selectedTrack = selectedTrack,
                    availableAudioTracks = availableAudioTracks,
                    selectedAudioTrack = selectedAudioTrack,
                    availableSubtitleTracks = availableSubtitleTracks,
                    selectedSubtitleTrack = selectedSubtitleTrack,
                    showQualityMenuChange = { showQualityMenu = it },
                    showAudioMenuChange = { showAudioMenu = it },
                    showSubtitleMenuChange = { showSubtitleMenu = it },
                    showScaleMenuChange = { showScaleMenu = it },
                    onSeek = { player.seekTo(it) }
                )
            }
            else -> {}
        }
    }
}
