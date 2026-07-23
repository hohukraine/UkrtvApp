package ua.ukrtv.app.ui.player

import android.view.KeyEvent
import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ua.ukrtv.app.util.AppLogger
import kotlin.math.roundToLong
import coil3.compose.AsyncImage
import coil3.request.crossfade
import coil3.request.ImageRequest
import androidx.compose.ui.layout.ContentScale


internal const val SEEK_STEP_MS = 10_000L
private const val PHONE_CONTROLS_HIDE_DELAY_MS = 3_000L
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
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playerType by viewModel.playerType.collectAsStateWithLifecycle()

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

    val state by viewModel.state.collectAsStateWithLifecycle()
    val engine = remember(playerType) {
        if (playerType == ua.ukrtv.app.util.PlayerType.BUILTIN) viewModel.getOrCreateEngine(context) else null
    }

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
    var seekProgress by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var showSeekOverlay by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }
    var brightness by remember { mutableFloatStateOf(-1f) }
    var gestureType by remember { mutableStateOf<GestureType?>(null) }
    var gestureStartX by remember { mutableFloatStateOf(0f) }
    var gestureStartY by remember { mutableFloatStateOf(0f) }
    var dragBasePosition by remember { mutableLongStateOf(0L) }
    var pendingSeekTarget by remember { mutableStateOf<Long?>(null) }
    val isPlaying = engine?.isPlaying ?: false

    val activity = LocalContext.current as? Activity
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowManager = LocalContext.current.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager

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
                delay(200)
                if (!isSeeking) {
                    currentPosition = engine?.currentPosition ?: 0L
                }
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
            delay(600)
            seekIndicator = null
        }
    }

    LaunchedEffect(isSeeking) {
        if (!isSeeking) {
            while (true) {
                delay(16)
                pendingSeekTarget?.let { target ->
                    val enginePos = engine?.currentPosition ?: 0L
                    if (kotlin.math.abs(enginePos - target) < 500) {
                        pendingSeekTarget = null
                    }
                }
                currentPosition = pendingSeekTarget ?: (engine?.currentPosition ?: 0L)
                duration = engine?.duration ?: 0L
            }
        }
    }

    val animatedCenterAlpha by animateFloatAsState(
        targetValue = if (showControls && state.status is PlayerStatus.Ready) 1f else 0f,
        animationSpec = tween(200),
        label = "centerAlpha"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                    .pointerInput(isSeeking) {
                        if (isSeeking) return@pointerInput
                        detectTapGestures(
                            onTap = {
                                engine?.setPlaybackSpeed(1f)
                                showControls = !showControls
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            onDoubleTap = { offset ->
                                val w = size.width
                                if (offset.x < w / 3f) {
                                    val pos = maxOf(0L, (engine?.currentPosition ?: 0L) - 10_000L)
                                    engine?.seekTo(pos)
                                    seekIndicator = Pair("-10s", -1f)
                                } else if (offset.x > w * 2f / 3f) {
                                    val pos = minOf(engine?.duration ?: 0L, (engine?.currentPosition ?: 0L) + 10_000L)
                                    engine?.seekTo(pos)
                                    seekIndicator = Pair("+10s", 1f)
                                } else {
                                    viewModel.togglePlay()
                                }
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            onLongPress = {
                                engine?.setPlaybackSpeed(2f)
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                gestureStartX = offset.x
                                gestureStartY = offset.y
                                gestureType = null
                                dragBasePosition = engine?.currentPosition ?: currentPosition
                            },
                            onDragEnd = {
                                if (gestureType == GestureType.VOLUME) {
                                    engine?.setVolume(volume)
                                } else if (gestureType == GestureType.BRIGHTNESS) {
                                    val lp = activity?.window?.attributes
                                    if (lp != null && brightness >= 0f) {
                                        lp.screenBrightness = brightness.coerceIn(0.01f, 1f)
                                        activity.window?.attributes = lp
                                    }
                                } else if (gestureType == GestureType.SEEK && isSeeking) {
                                    val targetMs = (seekProgress * duration).toLong()
                                    engine?.seekTo(targetMs)
                                    currentPosition = targetMs
                                    pendingSeekTarget = targetMs
                                    isSeeking = false
                                }
                                gestureType = null
                                showSeekOverlay = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dx = change.position.x - gestureStartX
                                val dy = change.position.y - gestureStartY
                                if (gestureType == null) {
                                    if (kotlin.math.abs(dx) > 30f || kotlin.math.abs(dy) > 30f) {
                                        val isHorizontal = kotlin.math.abs(dx) > kotlin.math.abs(dy)
                                        gestureType = if (isHorizontal) {
                                            GestureType.SEEK
                                        } else {
                                            if (gestureStartX > size.width / 2f) GestureType.VOLUME else GestureType.BRIGHTNESS
                                        }
                                        if (isHorizontal) showSeekOverlay = true
                                    }
                                }
                                when (gestureType) {
                                    GestureType.SEEK -> {
                                        isSeeking = true
                                        val seekDelta = (dx / size.width) * duration * 0.5f
                                        seekProgress = ((dragBasePosition + seekDelta) / duration).coerceIn(0f, 1f)
                                        currentPosition = (seekProgress * duration).toLong()
                                    }
                                    GestureType.VOLUME -> {
                                        volume = (volume - dragAmount.y / size.height).coerceIn(0f, 1f)
                                        engine?.setVolume(volume)
                                    }
                                    GestureType.BRIGHTNESS -> {
                                        val currentBright = activity?.window?.attributes?.screenBrightness ?: 0.5f
                                        brightness = (currentBright - dragAmount.y / size.height).coerceIn(0.01f, 1f)
                                        val lp = activity?.window?.attributes
                                        if (lp != null) {
                                            lp.screenBrightness = brightness
                                            activity.window?.attributes = lp
                                        }
                                    }
                                    null -> {}
                                }
                            }
                        )
                    }
            ) {
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
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Icon(
                                imageVector = if (indicator.second < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
                                contentDescription = null,
                                tint = brandColor,
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

                if (showSeekOverlay) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color(0xCC111111), RoundedCornerShape(24.dp))
                            .padding(horizontal = 48.dp, vertical = 32.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White,
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val diff = currentPosition - dragBasePosition
                                Text(
                                    text = formatTime(duration),
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (kotlin.math.abs(diff) >= 1000) {
                                    Box(
                                        modifier = Modifier
                                            .background(if (diff > 0) Color(0xFF2E7D32) else Color(0xFFC62828), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (diff > 0) "+${formatTime(diff)}" else formatTime(-diff),
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Canvas(
                                modifier = Modifier
                                    .width(240.dp)
                                    .height(6.dp)
                            ) {
                                val w = size.width
                                val seekFraction = (currentPosition.toFloat() / (duration.coerceAtLeast(1L).toFloat())).coerceIn(0f, 1f)
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset.Zero,
                                    size = Size(w, size.height),
                                    cornerRadius = CornerRadius(size.height / 2)
                                )
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset.Zero,
                                    size = Size(w * seekFraction, size.height),
                                    cornerRadius = CornerRadius(size.height / 2)
                                )
                            }
                        }
                    }
                }

                if (gestureType == GestureType.VOLUME) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 20.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (volume > 0f) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Canvas(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(80.dp)
                            ) {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.3f),
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(size.width / 2)
                                )
                                drawRoundRect(
                                    color = brandColor,
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, size.height * volume),
                                    cornerRadius = CornerRadius(size.width / 2)
                                )
                            }
                        }
                    }
                }

                if (gestureType == GestureType.BRIGHTNESS) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 20.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Canvas(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(80.dp)
                            ) {
                                val brightVal = if (brightness < 0f) 0.5f else brightness
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.3f),
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(size.width / 2)
                                )
                                drawRoundRect(
                                    color = brandColor,
                                    topLeft = Offset(size.width, size.height * (1f - brightVal)),
                                    size = Size(size.width, size.height * brightVal),
                                    cornerRadius = CornerRadius(size.width / 2)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showControls && state.status is PlayerStatus.Ready) {
            PhonePlayerControls(
                title = title,
                currentPosition = currentPosition,
                duration = duration,
                seekProgress = seekProgress,
                isPlaying = isPlaying,
                isSeeking = isSeeking,
                showSeekOverlay = showSeekOverlay,
                hasPrevious = viewModel.hasPreviousEpisode(),
                hasNext = viewModel.hasNextEpisode(),
                brandColor = brandColor,
                onBack = onBack,
                onTogglePlay = { viewModel.togglePlay() },
                onSeekTo = { pos -> engine?.seekTo(pos) },
                onPreviousEpisode = {
                    viewModel.navigateToPreviousEpisode()
                    lastInteractionTime = System.currentTimeMillis()
                },
                onNextEpisode = {
                    viewModel.saveProgress(currentPosition, duration)
                    viewModel.navigateToNextEpisode()
                    lastInteractionTime = System.currentTimeMillis()
                },
                onSeekStart = {
                    isSeeking = true
                    gestureType = GestureType.SEEK
                    showSeekOverlay = true
                    dragBasePosition = engine?.currentPosition ?: currentPosition
                },
                onSeekEnd = { targetMs ->
                    engine?.seekTo(targetMs)
                    currentPosition = targetMs
                    pendingSeekTarget = targetMs
                    isSeeking = false
                    gestureType = null
                    showSeekOverlay = false
                },
                onSeekDrag = { progress ->
                    seekProgress = progress
                    currentPosition = (progress * duration).toLong()
                },
                onRotate = { activity?.togglePlayerRotation(isLandscape) },
                onInteract = { lastInteractionTime = System.currentTimeMillis() },
                modifier = Modifier
                    .pointerInput(isSeeking) {
                        if (isSeeking) return@pointerInput
                        detectTapGestures { lastInteractionTime = System.currentTimeMillis() }
                    }
            )
        }

        val currentStatus = state.status
        EpisodeLoadingOverlay(
            poster = poster,
            season = state.currentSeason,
            episode = state.currentEpisode,
            visible = currentStatus is PlayerStatus.Loading
        )

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

private enum class GestureType { SEEK, VOLUME, BRIGHTNESS }

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}


