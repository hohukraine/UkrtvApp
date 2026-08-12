package ua.ukrtv.app.ui.player

import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.player.EmbeddedPlayerFactory
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.hasMediatekChipset

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

    val playerListener = remember {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val dur = player.duration
                    val effectiveDur = if (dur > 0) dur else 0L
                    viewModel.saveProgress(if (effectiveDur > 0) effectiveDur else player.currentPosition, effectiveDur)
                    if (viewModel.prepareNextEpisode()) {
                        viewModel.executePreparedNavigation()
                    } else {
                        onBack()
                    }
                }
            }
        }
    }

    DisposableEffect(player) {
        player.addListener(playerListener)
        onDispose { player.removeListener(playerListener) }
    }

    LaunchedEffect(uiState.status) {
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
        while (true) {
            delay(10_000)
            if (player.isPlaying) {
                viewModel.saveProgress(player.currentPosition, player.duration)
            }
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

    var showPicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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

        // Top bar: back + title (left), picker button (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                viewModel.saveProgress(player.currentPosition, player.duration)
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showPicker = !showPicker }) {
                Icon(Icons.Default.Settings, contentDescription = "Налаштування", tint = Color.White)
            }
        }

        if (showPicker) {
            PickerOverlay(
                player = player,
                uiState = uiState,
                onDismiss = { showPicker = false },
                onEpisodeSelected = { s, e, v ->
                    viewModel.saveProgress(player.currentPosition, player.duration)
                    viewModel.onEpisodeSelected(s, e, v)
                }
            )
        }
    }

    BackHandler {
        if (showPicker) {
            showPicker = false
        } else {
            viewModel.saveProgress(player.currentPosition, player.duration)
            onBack()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PickerOverlay(
    player: Player,
    uiState: PlayerState,
    onDismiss: () -> Unit,
    onEpisodeSelected: (Int, Int, String?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
                .background(Color(0xFF1A1A1A))
                .clickable { }
                .padding(16.dp)
        ) {
            Text("Налаштування", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

            // Audio tracks
            Text("Аудіо", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            val tracks = player.currentTracks
            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                tracks.groups.asSequence()
                    .filter { it.type == C.TRACK_TYPE_AUDIO }
                    .forEach { group ->
                        items(group.length) { trackIndex ->
                            val track = group.getTrackFormat(trackIndex)
                            val isSelected = group.isTrackSelected(trackIndex)
                            val label = buildString {
                                track.language?.let { append(it) }
                                track.label?.let {
                                    if (isNotEmpty()) append(" · ")
                                    append(it)
                                }
                                if (track.channelCount > 0) {
                                    if (isNotEmpty()) append(" · ")
                                    append("${track.channelCount}.0")
                                }
                                if (isBlank()) append("Доріжка ${trackIndex + 1}")
                            }
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFF8AB4F8) else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(
                                                TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                                            )
                                            .build()
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
            }

            // Seasons/Episodes if available
            if (uiState.availableSeasons != null) {
                Text("Серії", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    uiState.availableSeasons.forEach { season ->
                        item {
                            Text(
                                text = "Сезон ${season.number}",
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(season.episodes) { episode ->
                            val isCurrent = uiState.currentSeason == season.number && uiState.currentEpisode == episode.number
                            Text(
                                text = "Серія ${episode.number}${episode.title?.let { ": $it" } ?: ""}",
                                color = if (isCurrent) Color(0xFF8AB4F8) else Color.White,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onEpisodeSelected(season.number, episode.number, null)
                                        onDismiss()
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(SingletonComponent::class)
interface EmbeddedPlayerEntryPoint {
    fun embeddedPlayerFactory(): EmbeddedPlayerFactory
}
