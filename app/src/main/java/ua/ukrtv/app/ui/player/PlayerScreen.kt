package ua.ukrtv.app.ui.player

import android.util.Log
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults

import ua.ukrtv.app.player.PlaybackErrorHandler

import ua.ukrtv.app.ui.theme.BrandBlue

import ua.ukrtv.app.ui.components.SeasonEpisodePicker

import android.media.audiofx.LoudnessEnhancer
import android.os.Build

@UnstableApi
@Composable
fun PlayerScreen(
    uakinoUrl: String,
    contentId: String,
    title: String,
    season: Int? = null,
    episode: Int? = null,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val dataSourceFactory = remember { viewModel.getDataSourceFactory() }
    
    val player = remember { viewModel.buildPlayer(context, dataSourceFactory) }
    
    // Memory-efficient tracking and cleanup
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            private var loudnessEnhancer: LoudnessEnhancer? = null
            
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    try {
                        loudnessEnhancer?.release()
                        loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                            setTargetGain(0)
                            enabled = true
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerScreen", "LoudnessEnhancer error", e)
                    }
                }
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

    LaunchedEffect(uakinoUrl, contentId) {
        viewModel.initialize(
            contentId = contentId,
            title = title,
            uakinoUrl = uakinoUrl,
            season = season,
            episode = episode
        )
    }

    LaunchedEffect(player) {
        while (true) {
            kotlinx.coroutines.delay(10000)
            if (player.isPlaying && player.duration > 0) {
                viewModel.saveProgress(player.currentPosition, player.duration)
            }
        }
    }

    PlayerContent(
        state = state,
        player = player,
        title = title,
        season = season,
        episode = episode,
        viewModel = viewModel,
        onBack = onBack,
        pageUrl = uakinoUrl
    )
}



@UnstableApi
@Composable
private fun PlayerContent(
    state: PlayerUiState,
    player: ExoPlayer,
    title: String,
    season: Int?,
    episode: Int?,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    pageUrl: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0D))
            .onKeyEvent { keyEvent ->
                handleKeyEvent(keyEvent.nativeKeyEvent, player, viewModel) {
                    onBack()
                }
            }
    ) {
        when (val currentState = state) {
            is PlayerUiState.Idle, is PlayerUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF6E85B7))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "ПЕРЕВІРКА ПРОВАЙДЕРІВ...",
                            color = Color(0xFFE1E1E1).copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }
            is PlayerUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentState.message,
                            color = Color(0xFFE57373),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        androidx.tv.material3.Button(
                            onClick = { viewModel.retryWithNextProvider() },
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = Color(0xFF151517),
                                focusedContainerColor = Color(0xFFE1E1E1),
                                contentColor = Color(0xFFE1E1E1),
                                focusedContentColor = Color(0xFF0C0C0D)
                            ),
                            shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(8.dp))
                        ) {
                            Text("ПОВТОРИТИ", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            is PlayerUiState.Ready -> {
                val showControls by viewModel.showControls.collectAsState()
                val nextCountdown by viewModel.nextEpisodeCountdown.collectAsState()
                
                var showAudioMenu by remember { mutableStateOf(false) }
                var showSubtitleMenu by remember { mutableStateOf(false) }
                var showQualityMenu by remember { mutableStateOf(false) }
                
                LaunchedEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                viewModel.onPlaybackEnded()
                            }
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            Log.e("PlayerScreen", "Playback error: ${error.errorCodeName} (${error.errorCode}) - ${error.message}")
                            viewModel.onPlayerError(error)
                        }
                    }
                    player.addListener(listener)
                }

                LaunchedEffect(showControls) {
                    if (showControls) {
                        kotlinx.coroutines.delay(5000)
                        viewModel.setShowControls(false)
                    }
                }
                
                LaunchedEffect(currentState.url, currentState.loadTrigger) {
                    if (currentState.url.isNotEmpty()) {
                        val mediaItem = MediaItem.Builder()
                            .setUri(currentState.url)
                            .setMimeType(
                                when {
                                    currentState.url.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
                                    currentState.url.contains(".mpd") -> MimeTypes.APPLICATION_MPD
                                    else -> null
                                }
                            )
                            .build()
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        if (currentState.positionMs > 0) {
                            player.seekTo(currentState.positionMs)
                        }
                        player.playWhenReady = true
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                val isPlaying by viewModel.isPlaying.collectAsState()
                val syncEvent by viewModel.syncEvent.collectAsState()
                
                var currentPosition by remember { mutableStateOf(player.currentPosition) }
                var duration by remember { mutableStateOf(player.duration) }

                LaunchedEffect(isPlaying, showControls) {
                    if (showControls) {
                        while (true) {
                            currentPosition = player.currentPosition
                            duration = player.duration
                            kotlinx.coroutines.delay(1000)
                        }
                    }
                }

                if (showControls) {
                    PlayerOverlay(
                        title = title,
                        isPlaying = isPlaying,
                        positionMs = currentPosition,
                        durationMs = duration,
                        onPlayPauseToggle = {
                            viewModel.togglePlayPause()
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        onSeekBackward = { player.seekTo(maxOf(0L, player.currentPosition - 10_000)) },
                        onSeekForward = { player.seekTo(player.currentPosition + 10_000) },
                        onShowAudioMenu = { showAudioMenu = true },
                        onShowSubtitleMenu = { showSubtitleMenu = true },
                        onShowQualityMenu = { showQualityMenu = true },
                        nextCountdown = nextCountdown,
                        season = season,
                        episode = episode
                    )
                }

                syncEvent?.let { event ->
                    SyncProgressDialog(
                        remote = event.remote,
                        local = event.local,
                        onSync = { 
                            player.seekTo(event.remote.positionMs)
                            viewModel.dismissSyncDialog()
                        },
                        onDismiss = { 
                            viewModel.dismissSyncDialog()
                        }
                    )
                }

                if (showQualityMenu) {
                    TrackSelectionDialog(
                        tracks = player.currentTracks,
                        trackType = C.TRACK_TYPE_VIDEO,
                        onTrackSelected = { group, index ->
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                                .build()
                            showQualityMenu = false
                        },
                        onDismiss = { showQualityMenu = false }
                    )
                }

                if (showAudioMenu) {
                    TrackSelectionDialog(
                        tracks = player.currentTracks,
                        trackType = C.TRACK_TYPE_AUDIO,
                        onTrackSelected = { group, index ->
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                                .build()
                            showAudioMenu = false
                        },
                        onDismiss = { showAudioMenu = false }
                    )
                }

                if (showSubtitleMenu) {
                    TrackSelectionDialog(
                        tracks = player.currentTracks,
                        trackType = C.TRACK_TYPE_TEXT,
                        onTrackSelected = { group, index ->
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                                .build()
                            showSubtitleMenu = false
                        },
                        onDismiss = { showSubtitleMenu = false }
                    )
                }
            }
            is PlayerUiState.SeriesSelection -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0C0C0D))
                        .padding(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "Оберіть серію",
                            color = Color(0xFFE1E1E1),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.padding(bottom = 48.dp)
                        )
                        SeasonEpisodePicker(
                            seasons = currentState.seasons,
                            onEpisodeClick = { s, e -> viewModel.onEpisodeSelected(s, e) }
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SyncProgressDialog(
    remote: ua.ukrtv.app.domain.model.WatchProgress,
    local: ua.ukrtv.app.domain.model.WatchProgress,
    onSync: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            colors = SurfaceDefaults.colors(
                containerColor = Color(0xFF151517),
                contentColor = Color(0xFFE1E1E1)
            ),
            modifier = Modifier.width(420.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "СИНХРОНІЗАЦІЯ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6E85B7),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Знайдено прогрес на іншому пристрої (${formatTime(remote.positionMs)}). Тут ви зупинилися на ${formatTime(local.positionMs)}. Бажаєте продовжити з іншого пристрою?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.tv.material3.Button(
                        onClick = onDismiss,
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color(0xFF7A7A7A),
                            focusedContentColor = Color.White
                        )
                    ) {
                        Text("НІ", fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    androidx.tv.material3.Button(
                        onClick = onSync,
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = Color(0xFF6E85B7),
                            focusedContainerColor = Color.White,
                            contentColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Text("ТАК", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@UnstableApi
@Composable
private fun TrackSelectionDialog(
    tracks: Tracks,
    trackType: @C.TrackType Int,
    onTrackSelected: (Tracks.Group, Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(
                containerColor = Color(0xFF1A1A1A)
            ),
            modifier = Modifier
                .width(400.dp)
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val title = when(trackType) {
                    C.TRACK_TYPE_AUDIO -> "Вибір озвучки"
                    C.TRACK_TYPE_TEXT -> "Вибір субтитрів"
                    C.TRACK_TYPE_VIDEO -> "Вибір якості"
                    else -> "Налаштування"
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn {
                    val filteredGroups = tracks.groups.filter { it.type == trackType }
                    filteredGroups.forEach { group ->
                        items(group.length) { trackIndex ->
                            val format = group.getTrackFormat(trackIndex)
                            val isSelected = group.isTrackSelected(trackIndex)
                            
                            val trackName = when(trackType) {
                                C.TRACK_TYPE_VIDEO -> "${format.height}p"
                                else -> format.label ?: format.language ?: "Доріжка ${trackIndex + 1}"
                            }

                            Surface(
                                onClick = { onTrackSelected(group, trackIndex) },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) BrandBlue else Color.Transparent,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = trackName,
                                    modifier = Modifier.padding(12.dp),
                                    color = if (isSelected) Color.White else Color.Unspecified
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun handleKeyEvent(
    keyEvent: KeyEvent,
    player: ExoPlayer,
    viewModel: PlayerViewModel,
    onBack: () -> Unit
): Boolean {
    if (keyEvent.action != KeyEvent.ACTION_DOWN) return false
    
    val controlsVisible = viewModel.showControls.value
    
    if (!controlsVisible && (
        keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
        keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
        keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
        keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
    )) {
        viewModel.setShowControls(true)
        return true
    }

    when (keyEvent.keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            player.seekTo(maxOf(0L, player.currentPosition - 10_000))
            return true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            player.seekTo(player.currentPosition + 10_000)
            return true
        }
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
            return false 
        }
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
            if (player.isPlaying) player.pause() else player.play()
            viewModel.togglePlayPause()
            return true
        }
        KeyEvent.KEYCODE_BACK -> {
            if (controlsVisible) {
                viewModel.setShowControls(false)
                return true
            }
            onBack()
            return true
        }
    }
    return false
}
