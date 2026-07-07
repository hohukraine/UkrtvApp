package ua.ukrtv.app.ui.player

import android.view.KeyEvent
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
import ua.ukrtv.app.ui.theme.BrandBlue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import ua.ukrtv.app.util.AppLogger


internal const val SEEK_STEP_MS = 10_000L
private const val CONTROLS_HIDE_DELAY_MS = 5_000L
internal const val SKIP_INTRO_WINDOW_MS = 120_000L
internal const val SKIP_INTRO_STEP_MS = 90_000L
internal const val NEXT_EPISODE_COUNTDOWN_SEC = 10

private enum class HeldSeekDir { FORWARD, BACKWARD }

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
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val dataSourceFactory = remember { viewModel.getDataSourceFactory() }
    val player = remember { viewModel.getOrCreatePlayer(context, dataSourceFactory) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(player, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                player.playWhenReady = false
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.saveProgress(player.currentPosition, player.duration)
            viewModel.releasePlayer(player)
        }
    }

    LaunchedEffect(url, contentId, season, episode) {
        viewModel.initialize(contentId, title, url, season, episode, poster)
    }

    PlayerContent(
        state = state,
        player = player,
        viewModel = viewModel,
        brandColor = brandColor,
        onBack = onBack,
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
    title: String
) {
    val playFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }

    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.isShowingControls, state.status) {
        if (state.isShowingControls && state.status is PlayerStatus.Ready) {
            lastInteractionTime = System.currentTimeMillis()
            delay(150)
            try {
                playButtonFocusRequester.requestFocus()
                AppLogger.d("PlayerScreen", "Focus requested successfully")
            } catch (e: Exception) {
                AppLogger.w("PlayerScreen", "Focus request failed: ${e.message}")
            }
        }
    }

    LaunchedEffect(state.isShowingControls) {
        if (state.isShowingControls) {
            while (true) {
                delay(1000)
                val elapsed = System.currentTimeMillis() - lastInteractionTime
                if (elapsed >= CONTROLS_HIDE_DELAY_MS) {
                    viewModel.setShowControls(false)
                    break
                }
            }
        }
    }

    var heldSeekDir by remember { mutableStateOf<HeldSeekDir?>(null) }

    LaunchedEffect(heldSeekDir) {
        val dir = heldSeekDir ?: return@LaunchedEffect
        while (true) {
            delay(250)
            val pos = player.currentPosition
            if (dir == HeldSeekDir.FORWARD) {
                viewModel.seekTo(minOf(player.duration, pos + SEEK_STEP_MS))
            } else {
                viewModel.seekTo(maxOf(0L, pos - SEEK_STEP_MS))
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
                
                when (ke.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (state.isShowingControls) {
                            lastInteractionTime = System.currentTimeMillis()
                            return@onKeyEvent false
                        }
                        if (ke.action == android.view.KeyEvent.ACTION_DOWN) {
                            heldSeekDir = HeldSeekDir.BACKWARD
                            viewModel.seekTo(maxOf(0L, player.currentPosition - SEEK_STEP_MS))
                            return@onKeyEvent true
                        } else if (ke.action == android.view.KeyEvent.ACTION_UP) {
                            heldSeekDir = null
                            return@onKeyEvent true
                        }
                        return@onKeyEvent false
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (state.isShowingControls) {
                            lastInteractionTime = System.currentTimeMillis()
                            return@onKeyEvent false
                        }
                        if (ke.action == android.view.KeyEvent.ACTION_DOWN) {
                            heldSeekDir = HeldSeekDir.FORWARD
                            viewModel.seekTo(player.currentPosition + SEEK_STEP_MS)
                            return@onKeyEvent true
                        } else if (ke.action == android.view.KeyEvent.ACTION_UP) {
                            heldSeekDir = null
                            return@onKeyEvent true
                        }
                        return@onKeyEvent false
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (state.isShowingControls) {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                        return@onKeyEvent false
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        if (state.isShowingControls) return@onKeyEvent false
                        lastInteractionTime = System.currentTimeMillis()
                        viewModel.setShowControls(true)
                        return@onKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_BACK -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        onBack()
                        return@onKeyEvent true
                    }
                    else -> {}
                }
                false
            }
    ) {
        PlayerReadyContent(
            status = state.status,
            playerState = state,
            player = player,
            viewModel = viewModel,
            title = title,
            brandColor = brandColor,
            scaleMode = state.scaleMode,
            hasEpisodes = state.availableSeasons?.isNotEmpty() == true,
            playFocusRequester = playFocusRequester,
            playButtonFocusRequester = playButtonFocusRequester,
            isShowingControls = state.isShowingControls,
            onSeek = { player.seekTo(it) }
        )

        val currentStatus = state.status
        if (currentStatus is PlayerStatus.Loading) {
            Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF6E85B7))
            }
        }

        if (currentStatus is PlayerStatus.Error) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(currentStatus.message, color = Color.Red, fontSize = 18.sp)
                    androidx.tv.material3.Button(onClick = { viewModel.retry() }) {
                        Text("Retry")
                    }
                }
            }
        }

    }
}
