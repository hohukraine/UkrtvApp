package ua.ukrtv.app.ui.player

import android.view.KeyEvent
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.LocalFormFactor
import ua.ukrtv.app.player.PlaybackEngine
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import ua.ukrtv.app.util.AppLogger
import kotlin.math.roundToLong
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale


internal const val SEEK_STEP_MS = 10_000L
private const val CONTROLS_HIDE_DELAY_MS = 5_000L
private const val PHONE_CONTROLS_HIDE_DELAY_MS = 3_000L
internal const val SKIP_INTRO_WINDOW_MS = 120_000L
internal const val SKIP_INTRO_STEP_MS = 90_000L
internal const val NEXT_EPISODE_COUNTDOWN_SEC = 10

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
    val playerType by viewModel.playerType.collectAsState()

    if (playerType == ua.ukrtv.app.util.PlayerType.EXTERNAL_PLAYER) {
        ExternalPlayerScreen(
            url = url,
            contentId = contentId,
            title = title,
            poster = poster,
            season = season,
            episode = episode,
            onBack = onBack,
            viewModel = viewModel
        )
        return
    }

    val state by viewModel.state.collectAsState()
    val engine = remember { viewModel.getOrCreateEngine(context) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(engine, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    engine?.let { viewModel.onBackgroundTransition(it.currentPosition, it.duration) }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    viewModel.onForegroundTransition()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine?.let {
                viewModel.saveProgress(it.currentPosition, it.duration)
                viewModel.releaseEngine()
            }
        }
    }

    LaunchedEffect(url, contentId, season, episode) {
        viewModel.initialize(contentId, title, url, season, episode, poster)
    }

    val formFactor = LocalFormFactor.current
    when (formFactor) {
        FormFactor.TV -> TvPlayerContent(
            state = state,
            engine = engine,
            viewModel = viewModel,
            brandColor = brandColor,
            onBack = onBack,
            title = title,
            poster = poster
        )
        FormFactor.PHONE, FormFactor.TABLET -> PhonePlayerContent(
            state = state,
            engine = engine,
            viewModel = viewModel,
            brandColor = brandColor,
            onBack = onBack,
            title = title,
            poster = poster
        )
    }
}

@UnstableApi
@Composable
private fun TvPlayerContent(
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

    LaunchedEffect(heldSeekDir) {
        val dir = heldSeekDir ?: return@LaunchedEffect
        val startTime = System.currentTimeMillis()
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
            val pos = engine?.currentPosition ?: 0L
            if (dir == HeldSeekDir.FORWARD) {
                viewModel.seekTo(minOf(engine?.duration ?: 0L, pos + step))
            } else {
                viewModel.seekTo(maxOf(0L, pos - step))
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
            showOverlay = true,
            onSeek = { engine?.seekTo(it) }
        )

        val currentStatus = state.status
        if (currentStatus is PlayerStatus.Loading) {
            EpisodeLoadingOverlay(
                poster = poster,
                season = state.currentSeason,
                episode = state.currentEpisode,
                brandColor = brandColor
            )
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

@UnstableApi
@Composable
private fun PhonePlayerContent(
    state: PlayerState,
    engine: PlaybackEngine?,
    viewModel: PlayerViewModel,
    brandColor: Color = BrandBlue,
    onBack: () -> Unit,
    title: String,
    poster: String = ""
) {
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var currentPosition by remember(engine) { mutableStateOf(engine?.currentPosition ?: 0L) }
    var duration by remember(engine) { mutableStateOf(engine?.duration ?: 0L) }
    var seekIndicator by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var showControls by remember { mutableStateOf(false) }
    val isPlaying = engine?.isPlaying ?: false

    val activity = LocalContext.current as? Activity
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(Unit) {
        activity?.applyPlayerOrientation(allowRotation = true)
        onDispose { activity?.applyPlayerOrientation(allowRotation = false) }
    }

    DisposableEffect(isLandscape) {
        activity?.setImmersive(isLandscape)
        onDispose { activity?.setImmersive(false) }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            while (true) {
                delay(500)
                currentPosition = engine?.currentPosition ?: 0L
                duration = engine?.duration ?: 0L
                val elapsed = System.currentTimeMillis() - lastInteractionTime
                if (elapsed >= PHONE_CONTROLS_HIDE_DELAY_MS) {
                    showControls = false
                    break
                }
            }
        }
    }

    LaunchedEffect(seekIndicator) {
        seekIndicator?.let {
            delay(700)
            seekIndicator = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlayerReadyContent(
            status = state.status,
            playerState = state,
            engine = engine,
            viewModel = viewModel,
            title = title,
            brandColor = brandColor,
            scaleMode = state.scaleMode,
            hasEpisodes = state.availableSeasons?.isNotEmpty() == true,
            playFocusRequester = remember { FocusRequester() },
            playButtonFocusRequester = remember { FocusRequester() },
            isShowingControls = showControls,
            heldSeekDir = null,
            showOverlay = false,
            onSeek = { engine?.seekTo(it) }
        )

        if (state.status is PlayerStatus.Ready) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            onDoubleTap = { offset ->
                                val w = size.width
                                if (offset.x < w / 2f) {
                                    val pos = maxOf(0L, (engine?.currentPosition ?: 0L) - 10_000L)
                                    engine?.seekTo(pos)
                                    seekIndicator = Pair("-10s", -1f)
                                } else {
                                    val pos = minOf(engine?.duration ?: 0L, (engine?.currentPosition ?: 0L) + 10_000L)
                                    engine?.seekTo(pos)
                                    seekIndicator = Pair("+10s", 1f)
                                }
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        )
                    }
            ) {
                val indicatorAlpha by animateFloatAsState(
                    targetValue = if (seekIndicator != null) 1f else 0f,
                    animationSpec = tween(if (seekIndicator != null) 120 else 250),
                    label = "seekAlpha"
                )

                AnimatedVisibility(
                    visible = seekIndicator != null,
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(250)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    val indicator = seekIndicator
                    if (indicator != null) {
                        val offsetX = if (indicator.second < 0) (-40).dp else 40.dp
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .offset(x = offsetX)
                                .alpha(indicatorAlpha)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Icon(
                                imageVector = if (indicator.second < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "10с",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls && state.status is PlayerStatus.Ready,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 80.dp)
                    )
                    IconButton(
                        onClick = { activity?.togglePlayerRotation(isLandscape) },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.ScreenRotation, contentDescription = "Повернути екран", tint = Color.White)
                    }
                }

                Spacer(Modifier.weight(1f))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            viewModel.togglePlay()
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Відтворити",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Slider(
                        value = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { ratio ->
                            engine?.seekTo((ratio * duration).roundToLong())
                            currentPosition = engine?.currentPosition ?: 0L
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = brandColor,
                            activeTrackColor = brandColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (viewModel.hasPreviousEpisode()) {
                            IconButton(onClick = {
                                viewModel.navigateToPreviousEpisode()
                                lastInteractionTime = System.currentTimeMillis()
                            }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Попередня серія", tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                        } else {
                            Spacer(Modifier.size(40.dp))
                        }

                        IconButton(onClick = {
                            engine?.let {
                                it.seekTo(maxOf(0L, it.currentPosition - 10_000L))
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.FastRewind, contentDescription = "-10с", tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        IconButton(onClick = {
                            viewModel.togglePlay()
                            lastInteractionTime = System.currentTimeMillis()
                        }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Пауза" else "Відтворити",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(onClick = {
                            engine?.let {
                                it.seekTo(minOf(it.duration, it.currentPosition + 10_000L))
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.FastForward, contentDescription = "+10с", tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        if (viewModel.hasNextEpisode()) {
                            IconButton(onClick = {
                                viewModel.saveProgress(currentPosition, duration)
                                viewModel.navigateToNextEpisode()
                                lastInteractionTime = System.currentTimeMillis()
                            }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Наступна серія", tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                        } else {
                            Spacer(Modifier.size(40.dp))
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        val currentStatus = state.status
        if (currentStatus is PlayerStatus.Loading) {
            EpisodeLoadingOverlay(
                poster = poster,
                season = state.currentSeason,
                episode = state.currentEpisode,
                brandColor = brandColor
            )
        }

        if (currentStatus is PlayerStatus.Error) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(currentStatus.message, color = Color.Red, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(brandColor, RoundedCornerShape(8.dp))
                            .clickable { viewModel.retry() }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Повторити", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeLoadingOverlay(
    poster: String,
    season: Int?,
    episode: Int?,
    brandColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (poster.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(poster)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .width(200.dp)
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(20.dp))
            }
            Text(
                "Наступна серія",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            if (season != null && episode != null) {
                Text(
                    "Сезон $season, Серія $episode",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))
            }
            CircularProgressIndicator(color = brandColor)
            Spacer(Modifier.height(8.dp))
            Text("Завантаження...", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@UnstableApi
@Composable
private fun ExternalPlayerScreen(
    url: String,
    contentId: String,
    title: String,
    poster: String,
    season: Int?,
    episode: Int?,
    onBack: () -> Unit,
    viewModel: PlayerViewModel
) {
    val state by viewModel.state.collectAsState()
    var playerLaunched by remember { mutableStateOf(viewModel.hasPendingExternalPlayerResult()) }

    val externalPlayerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AppLogger.d("ExternalPlayer", "Result received: code=${result.resultCode} hasData=${result.data != null}")
        val returnResult = viewModel.handleExternalPlayerResult(result.resultCode, result.data)
        when (returnResult) {
            is ua.ukrtv.app.ui.player.ExternalPlayerReturnResult.Advanced -> {
                playerLaunched = false
            }
            else -> {
                onBack()
            }
        }
    }

    LaunchedEffect(url, contentId, season, episode) {
        viewModel.initialize(contentId, title, url, season, episode, poster)
    }

    LaunchedEffect(state.status) {
        val status = state.status
        if (status is PlayerStatus.Ready && !playerLaunched) {
            delay(1500)
            playerLaunched = true
            viewModel.saveBeforeExternalPlayerLaunch()
            val intent = viewModel.createExternalPlayerIntent()
            if (intent != null) {
                try {
                    externalPlayerLauncher.launch(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    val playerLabel = viewModel.getCurrentExternalPlayerInfo()?.label ?: "плеєр"
                    AppLogger.w("ExternalPlayer", "Player not found: $playerLabel")
                    onBack()
                }
            } else {
                onBack()
            }
        }
    }

    val currentStatus = state.status
    val playerLabel = remember { viewModel.getCurrentExternalPlayerInfo()?.label ?: "плеєр" }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            currentStatus is PlayerStatus.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(currentStatus.message, color = Color.Red, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF6E85B7), RoundedCornerShape(8.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Назад", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            currentStatus is PlayerStatus.Loading -> {
                EpisodeLoadingOverlay(
                    poster = poster,
                    season = state.currentSeason,
                    episode = state.currentEpisode,
                    brandColor = Color(0xFF6E85B7)
                )
            }
            currentStatus is PlayerStatus.Ready && playerLaunched -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF6E85B7))
                    Spacer(Modifier.height(16.dp))
                    Text("Відкриття $playerLabel...", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF6E85B7))
                    Spacer(Modifier.height(16.dp))
                    Text("Завантаження...", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }
        }
    }
}
