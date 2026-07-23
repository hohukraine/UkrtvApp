package ua.ukrtv.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import kotlinx.coroutines.delay
import ua.ukrtv.app.player.PlaybackEngine
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.util.AppLogger

private const val CONTROLS_HIDE_DELAY_MS = 5_000L

private const val HELD_SEEK_INITIAL_STEP_MS = 10_000L
private const val HELD_SEEK_INITIAL_INTERVAL_MS = 250L
private const val HELD_SEEK_MID_STEP_MS = 30_000L
private const val HELD_SEEK_MID_INTERVAL_MS = 200L
private const val HELD_SEEK_MAX_STEP_MS = 60_000L
private const val HELD_SEEK_MAX_INTERVAL_MS = 167L
private const val HELD_SEEK_MID_THRESHOLD_MS = 1_000L
private const val HELD_SEEK_MAX_THRESHOLD_MS = 3_000L

private enum class HeldSeekDir { FORWARD, BACKWARD }

@UnstableApi
@Composable
fun TvPlayerContent(
    state: PlayerState,
    engine: PlaybackEngine?,
    viewModel: PlayerViewModel,
    brandColor: Color = BrandBlue,
    onBack: () -> Unit,
    title: String,
    poster: String = ""
) {
    val playFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }

    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.isShowingControls) {
        if (state.isShowingControls && state.status is PlayerStatus.Ready) {
            lastInteractionTime = System.currentTimeMillis()
            withFrameNanos { }
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
        } else {
            try {
                playFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    var heldSeekDir by remember { mutableStateOf<HeldSeekDir?>(null) }
    var heldSeekTarget by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(heldSeekDir) {
        if (heldSeekDir == null) { heldSeekTarget = null; return@LaunchedEffect }
        val dir = heldSeekDir!!
        val startTime = System.currentTimeMillis()
        var accumulated = engine?.currentPosition ?: 0L
        val duration = engine?.duration ?: 0L
        heldSeekTarget = accumulated
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val step = when {
                elapsed >= HELD_SEEK_MAX_THRESHOLD_MS -> HELD_SEEK_MAX_STEP_MS
                elapsed >= HELD_SEEK_MID_THRESHOLD_MS -> HELD_SEEK_MID_STEP_MS
                else -> HELD_SEEK_INITIAL_STEP_MS
            }
            val interval = when {
                elapsed >= HELD_SEEK_MAX_THRESHOLD_MS -> HELD_SEEK_MAX_INTERVAL_MS
                elapsed >= HELD_SEEK_MID_THRESHOLD_MS -> HELD_SEEK_MID_INTERVAL_MS
                else -> HELD_SEEK_INITIAL_INTERVAL_MS
            }
            delay(interval)
            if (dir == HeldSeekDir.FORWARD) {
                accumulated = minOf(duration, accumulated + step)
            } else {
                accumulated = maxOf(0L, accumulated - step)
            }
            heldSeekTarget = accumulated
            viewModel.seekTo(accumulated)
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
                            viewModel.seekTo(maxOf(0L, (engine?.currentPosition ?: 0L) - SEEK_STEP_MS))
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
                            viewModel.seekTo((engine?.currentPosition ?: 0L) + SEEK_STEP_MS)
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
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        lastInteractionTime = System.currentTimeMillis()
                        viewModel.togglePlay()
                        return@onKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    android.view.KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        if (!viewModel.hasPreviousEpisode()) return@onKeyEvent false
                        lastInteractionTime = System.currentTimeMillis()
                        engine?.let { viewModel.saveProgress(it.currentPosition, it.duration) }
                        viewModel.navigateToPreviousEpisode()
                        return@onKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                    android.view.KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        if (!viewModel.hasNextEpisode()) return@onKeyEvent false
                        lastInteractionTime = System.currentTimeMillis()
                        engine?.let { viewModel.saveProgress(it.currentPosition, it.duration) }
                        viewModel.navigateToNextEpisode()
                        return@onKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        lastInteractionTime = System.currentTimeMillis()
                        viewModel.setShowControls(true)
                        viewModel.seekTo((engine?.currentPosition ?: 0L) + SEEK_STEP_MS)
                        return@onKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        lastInteractionTime = System.currentTimeMillis()
                        viewModel.setShowControls(true)
                        viewModel.seekTo(maxOf(0L, (engine?.currentPosition ?: 0L) - SEEK_STEP_MS))
                        return@onKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_BACK -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        onBack()
                        return@onKeyEvent true
                    }
                    else -> {
                        if (ke.action == android.view.KeyEvent.ACTION_DOWN) {
                            AppLogger.d("PlayerScreen", "Unhandled key: keyCode=${ke.keyCode}")
                        }
                    }
                }
                false
            }
    ) {
        PlayerReadyContent(
            status = state.status,
            playerState = state,
            engine = engine,
            viewModel = viewModel,
            title = title,
            brandColor = brandColor,
            scaleMode = state.scaleMode,
            hasEpisodes = state.availableSeasons?.isNotEmpty() == true,
            playFocusRequester = playFocusRequester,
            playButtonFocusRequester = playButtonFocusRequester,
            isShowingControls = state.isShowingControls,
            heldSeekDir = heldSeekDir?.let {
                when (it) {
                    HeldSeekDir.FORWARD -> SeekDirection.Forward
                    HeldSeekDir.BACKWARD -> SeekDirection.Backward
                }
            },
            heldSeekTarget = heldSeekTarget,
            showOverlay = true,
            onSeek = { engine?.seekTo(it) }
        )

        val currentStatus = state.status

        if (currentStatus is PlayerStatus.Loading) {
            EpisodeLoadingOverlay(
                poster = poster,
                season = state.currentSeason,
                episode = state.currentEpisode,
                visible = true
            )
        }

        if (currentStatus is PlayerStatus.Error) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(currentStatus.message, color = Color.Red, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.retry() }) {
                        Text("Retry")
                    }
                }
            }
        }

        val isPaused = currentStatus is PlayerStatus.Ready && !state.isPlaying
        AnimatedVisibility(
            visible = isPaused,
            enter = fadeIn(tween(350)),
            exit = fadeOut(tween(350)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f)
                            ),
                            radius = 0.75f
                        )
                    )
            )
        }
    }
}
