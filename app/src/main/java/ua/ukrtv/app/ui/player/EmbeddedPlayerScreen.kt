package ua.ukrtv.app.ui.player

import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.player.EmbeddedPlayerFactory
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.hasMediatekChipset
import java.util.Locale

private const val SURFACE_TYPE_SURFACE_VIEW = 1
private const val SURFACE_TYPE_TEXTURE_VIEW = 2
private const val BLACK_SCREEN_CHECK_DELAY_MS = 4_000L

@OptIn(UnstableApi::class)
@Composable
fun EmbeddedPlayerScreen(
    contentId: String,
    title: String,
    url: String,
    poster: String,
    season: Int?,
    episode: Int?,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val playerFactory = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            EmbeddedPlayerEntryPoint::class.java
        ).embeddedPlayerFactory()
    }

    val thermalMonitor = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            EmbeddedPlayerEntryPoint::class.java
        ).thermalMonitor()
    }

    val player = remember { playerFactory.createPlayer() }

    var surfaceType by remember { mutableIntStateOf(
        if (hasMediatekChipset()) SURFACE_TYPE_TEXTURE_VIEW
        else SURFACE_TYPE_SURFACE_VIEW
    ) }

    val window = (context as? android.app.Activity)?.window
    DisposableEffect(Unit) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            player.release()
        }
    }

    LaunchedEffect(contentId, season, episode) {
        viewModel.initialize(contentId, title, url, season, episode, poster)
    }

    var autoAdvancing by remember { mutableStateOf(false) }
    var videoQualityCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val advanceToNextEpisode: (exitIfNoNext: Boolean) -> Unit by rememberUpdatedState { exitIfNoNext ->
        if (autoAdvancing) return@rememberUpdatedState
        autoAdvancing = true
        viewModel.saveProgress(player.currentPosition, player.duration)
        if (viewModel.prepareNextEpisode()) {
            viewModel.executePreparedNavigation()
        } else {
            scope.launch {
                val ok = viewModel.ensureSeasons() && viewModel.prepareNextEpisode()
                if (ok) {
                    viewModel.executePreparedNavigation()
                } else if (exitIfNoNext) {
                    onBack()
                }
                autoAdvancing = false
            }
        }
    }

    val playerListener = remember {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val dur = player.duration
                    val effectiveDur = if (dur > 0) dur else 0L
                    viewModel.saveProgress(if (effectiveDur > 0) effectiveDur else player.currentPosition, effectiveDur)
                    advanceToNextEpisode(true)
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val count = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .sumOf { it.length }
                if (count != videoQualityCount) {
                    videoQualityCount = count
                    AppLogger.d(
                        "EmbeddedPlayer",
                        "Manifest video renditions: $count | ${player.currentMediaItem?.localConfiguration?.uri}"
                    )
                }
            }
        }
    }

    DisposableEffect(player) {
        player.addListener(playerListener)
        onDispose { player.removeListener(playerListener) }
    }

    LaunchedEffect(uiState.status) {
        autoAdvancing = false
        val status = uiState.status
        if (status is PlayerStatus.Ready) {
            val mimeType = when (status.streamType) {
                StreamType.HLS -> MimeTypes.APPLICATION_M3U8
                StreamType.MPD -> MimeTypes.APPLICATION_MPD
                StreamType.MP4 -> MimeTypes.VIDEO_MP4
                else -> null
            }
            playerFactory.setDefaultRequestProperties(
                if (status.referer.isNotBlank()) mapOf("Referer" to status.referer) else emptyMap()
            )
            val mediaItem = MediaItem.Builder()
                .setUri(status.url)
                .apply { if (mimeType != null) setMimeType(mimeType) }
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            if (status.positionMs > 0) {
                player.seekTo(status.positionMs)
            }
            player.play()
        }
    }

    LaunchedEffect(player) {
        var stallTicks = 0
        while (true) {
            delay(10_000)
            if (player.isPlaying) {
                viewModel.saveProgress(player.currentPosition, player.duration)
                stallTicks = 0
            } else if (player.playbackState == Player.STATE_READY) {
                val dur = player.duration
                val atEnd = dur > 0 && player.currentPosition >= dur - 3_000
                stallTicks = if (atEnd) stallTicks + 1 else 0
            } else {
                stallTicks = 0
            }
            if (stallTicks >= 2 && uiState.status is PlayerStatus.Ready) {
                stallTicks = 0
                advanceToNextEpisode(true)
            }
        }
    }

    LaunchedEffect(player) {
        thermalMonitor.thermalStatus.collect { status ->
            playerFactory.applyThermalToPlayer(
                player,
                thermalMonitor.getQualityLevel(status)
            )
        }
    }

    var firstFrameRendered by remember { mutableStateOf(false) }
    val frameListener = remember {
        object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }
        }
    }
    DisposableEffect(player) {
        player.addListener(frameListener)
        onDispose { player.removeListener(frameListener) }
    }

    // Black-screen safety net: if playback is ready+playing but the surface never rendered
    // a frame within the timeout, recreate the PlayerView with TextureView. Re-armed on every
    // surface switch so the fallback itself can be verified.
    LaunchedEffect(uiState.status, surfaceType) {
        if (uiState.status is PlayerStatus.Ready && surfaceType == SURFACE_TYPE_SURFACE_VIEW) {
            firstFrameRendered = false
            delay(BLACK_SCREEN_CHECK_DELAY_MS)
            if (player.isPlaying && player.playbackState == Player.STATE_READY && !firstFrameRendered) {
                AppLogger.w("EmbeddedPlayer", "Safety net triggered: switching to TextureView")
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.saveProgress(player.currentPosition, player.duration)
                    player.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }

    var showControls by remember { mutableStateOf(true) }

    var seekAccumMs by remember { mutableStateOf(0L) }
    var seekTrigger by remember { mutableStateOf(0L) }
    var playerStats by remember { mutableStateOf("") }

    fun performSeek(forward: Boolean, stepMs: Long) {
        if (player.duration > 0) {
            val delta = if (forward) stepMs else -stepMs
            player.seekTo((player.currentPosition + delta).coerceIn(0L, player.duration))
            seekAccumMs += delta
        }
        seekTrigger++
    }

    var decoderNames by remember { mutableStateOf<Pair<String?, String?>>(null to null) }
    val analyticsListener = remember {
        object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializationDurationMs: Long
            ) {
                decoderNames = decoderNames.copy(first = decoderName)
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializationDurationMs: Long
            ) {
                decoderNames = decoderNames.copy(second = decoderName)
            }
        }
    }
    DisposableEffect(player) {
        player.addAnalyticsListener(analyticsListener)
        onDispose { player.removeAnalyticsListener(analyticsListener) }
    }

    LaunchedEffect(seekTrigger) {
        if (seekAccumMs != 0L) {
            delay(700)
            seekAccumMs = 0L
        }
    }

    LaunchedEffect(uiState.audioMode) {
        player.volume = uiState.audioMode.volume
    }

    LaunchedEffect(uiState.status, player, videoQualityCount) {
        while (uiState.status is PlayerStatus.Ready) {
            playerStats = buildPlayerStats(player, decoderNames.first, decoderNames.second, videoQualityCount)
            delay(1000)
        }
        playerStats = ""
    }

    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(uiState.status, showControls) {
        if (uiState.status is PlayerStatus.Ready && showControls) {
            lastInteractionTime = System.currentTimeMillis()
            delay(150)
            try {
                playButtonFocusRequester.requestFocus()
                AppLogger.d("EmbeddedPlayer", "Play button focus requested")
            } catch (e: Exception) {
                AppLogger.w("EmbeddedPlayer", "Play button focus failed: ${e.message}")
            }
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            lastInteractionTime = System.currentTimeMillis()
            delay(150)
            try {
                playButtonFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else {
            try {
                rootFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            while (true) {
                delay(1000)
                if (System.currentTimeMillis() - lastInteractionTime >= 4000) {
                    showControls = false
                    break
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                val ke = event.nativeKeyEvent
                when (ke.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (showControls) {
                            lastInteractionTime = System.currentTimeMillis()
                            return@onKeyEvent false
                        }
                        if (ke.action == android.view.KeyEvent.ACTION_DOWN) {
                            performSeek(forward = false, stepMs = seekStepForRepeat(ke.repeatCount))
                            return@onKeyEvent true
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (showControls) {
                            lastInteractionTime = System.currentTimeMillis()
                            return@onKeyEvent false
                        }
                        if (ke.action == android.view.KeyEvent.ACTION_DOWN) {
                            performSeek(forward = true, stepMs = seekStepForRepeat(ke.repeatCount))
                            return@onKeyEvent true
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (showControls) {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                        return@onKeyEvent false
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        if (showControls) return@onKeyEvent false
                        showControls = true
                        lastInteractionTime = System.currentTimeMillis()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        if (player.isPlaying) player.pause() else player.play()
                        lastInteractionTime = System.currentTimeMillis()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                    android.view.KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        if (!viewModel.hasNextEpisode()) return@onKeyEvent false
                        advanceToNextEpisode(false)
                        lastInteractionTime = System.currentTimeMillis()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    android.view.KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        if (!viewModel.hasPreviousEpisode()) return@onKeyEvent false
                        viewModel.saveProgress(player.currentPosition, player.duration)
                        viewModel.navigateToPreviousEpisode()
                        lastInteractionTime = System.currentTimeMillis()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        performSeek(forward = true, stepMs = seekStepForRepeat(ke.repeatCount))
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        performSeek(forward = false, stepMs = seekStepForRepeat(ke.repeatCount))
                        true
                    }
                    else -> false
                }
            }
    ) {
        key(surfaceType) {
            AndroidView(
                factory = { ctx ->
                    val layoutId = if (surfaceType == SURFACE_TYPE_TEXTURE_VIEW) {
                        ua.ukrtv.app.R.layout.player_view_texture
                    } else {
                        ua.ukrtv.app.R.layout.player_view_surface
                    }
                    (LayoutInflater.from(ctx).inflate(layoutId, null) as PlayerView).apply {
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.player = player
                }
            )
        }

        PlayerSeekIndicator(
            brandColor = BrandBlue,
            deltaMs = seekAccumMs,
            modifier = Modifier.align(Alignment.Center)
        )

        if (uiState.status is PlayerStatus.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (uiState.status is PlayerStatus.Error) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = (uiState.status as PlayerStatus.Error).message,
                        color = Color.Red,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF6E85B7))
                            .clickable { viewModel.retry() }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Повторити", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Top bar moved into PlayerControlsOverlay

        if (showControls) {
            val pickerColumns = buildPickerColumns(uiState)
            val pickerFocusedIndex = if (pickerColumns.isNotEmpty()) {
                uiState.pickerFocusedIndex.coerceIn(0, pickerColumns.lastIndex)
            } else 0
            PlayerControlsOverlay(
                visible = showControls,
                title = title,
                season = uiState.currentSeason,
                episode = uiState.currentEpisode,
                currentVoiceover = uiState.currentVoiceover,
                stats = playerStats,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition,
                durationMs = player.duration,
                bufferedPositionMs = player.bufferedPosition,
                brandColor = BrandBlue,
                hasNextEpisode = uiState.availableSeasons != null && viewModel.hasNextEpisode(),
                pickerColumns = pickerColumns,
                pickerFocusedIndex = pickerFocusedIndex,
                onPlayPauseToggle = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                onSeekBackward = {
                    performSeek(forward = false, stepMs = 10_000L)
                },
                onSeekForward = {
                    performSeek(forward = true, stepMs = 10_000L)
                },
                onNextEpisode = {
                    advanceToNextEpisode(false)
                },
                onPickerColumnFocused = { viewModel.onPickerColumnFocused(it) },
                onPickerValueChange = { direction ->
                    val col = pickerColumns.getOrNull(pickerFocusedIndex) ?: return@PlayerControlsOverlay
                    when (col.id) {
                        "audio_mode" -> viewModel.cycleAudioMode(direction)
                        else -> viewModel.onPickerValueChange(direction)
                    }
                },
                onPickerCommit = { viewModel.onPickerCommit() },
                playFocusRequester = playButtonFocusRequester
            )
        }
    }

    BackHandler {
        if (showControls) {
            showControls = false
        } else {
            viewModel.saveProgress(player.currentPosition, player.duration)
            onBack()
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(SingletonComponent::class)
interface EmbeddedPlayerEntryPoint {
    fun embeddedPlayerFactory(): EmbeddedPlayerFactory
    fun thermalMonitor(): ua.ukrtv.app.player.ThermalMonitor
}

private fun buildPickerColumns(
    uiState: PlayerState
): List<PickerColumn> {
    val cols = uiState.pickerColumns.toMutableList()

    cols.add(PickerColumn(id = "audio_mode", label = "АУДІО", value = uiState.audioMode.label))

    return cols
}

private fun buildPlayerStats(
    player: ExoPlayer,
    videoDecoder: String?,
    audioDecoder: String?,
    videoQualityCount: Int
): String {
    val parts = mutableListOf<String>()
    val fmt = player.videoFormat
    if (fmt != null && (fmt.width ?: 0) > 0 && (fmt.height ?: 0) > 0) {
        parts += "${fmt.width}x${fmt.height}"
        if (fmt.bitrate > 0) parts += String.format(Locale.US, "%.1f Мбіт/с", fmt.bitrate / 1_000_000f)
        (fmt.frameRate ?: 0f).takeIf { it > 0f }?.let { parts += "${it.toInt()}fps" }
    }
    friendlyCodec(fmt?.codecs, fmt?.sampleMimeType)?.let { parts += it }
    if (videoQualityCount > 0) parts += "$videoQualityCount якостей"
    videoDecoder?.let { parts += shortDecoderName(it) }
    audioDecoder?.let { parts += shortDecoderName(it) }
    return parts.joinToString(" \u00b7 ")
}

private fun friendlyCodec(codecs: String?, mime: String?): String? {
    val code = codecs?.substringBefore(".")?.lowercase()
    val mimeCode = mime?.substringAfterLast("/", "")?.lowercase()
    val short = code ?: mimeCode
    return when {
        short == "avc1" || mimeCode == "avc" -> "H.264"
        short == "hev1" || short == "hvc1" || mimeCode == "hevc" -> "HEVC"
        short == "vp9" || mimeCode == "vp9" -> "VP9"
        short == "av01" || mimeCode == "av1" -> "AV1"
        short == "mp4a" || short == "aac" -> "AAC"
        short == "opus" || mimeCode == "opus" -> "Opus"
        else -> short?.uppercase()
    }
}

private fun shortDecoderName(name: String): String {
    var n = name
    listOf("c2.android.", "c2.google.", "c2.ms.", "c2.mtk.", "OMX.google.", "OMX.qcom.", "OMX.mtk.", "OMX.").forEach {
        if (n.startsWith(it)) n = n.removePrefix(it)
    }
    return n
}

private fun seekStepForRepeat(repeatCount: Int): Long = when {
    repeatCount <= 0 -> 10_000L
    repeatCount < 3 -> 30_000L
    else -> 60_000L
}
