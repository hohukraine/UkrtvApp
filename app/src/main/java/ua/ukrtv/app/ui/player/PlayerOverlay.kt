package ua.ukrtv.app.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Dvr
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import ua.ukrtv.app.ui.theme.BrandBlue

import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerOverlay(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long = 0L,
    onPlayPauseToggle: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeek: (Float) -> Unit,
    onShowAudioMenu: () -> Unit,
    onShowSubtitleMenu: () -> Unit,
    onShowQualityMenu: () -> Unit,
    onPreviousEpisode: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    hasPreviousEpisode: Boolean = false,
    hasNextEpisode: Boolean = false,
    onToggleCodec: () -> Unit,
    codecPolicy: ua.ukrtv.app.player.CodecPolicy = ua.ukrtv.app.player.CodecPolicy.AUTO,
    nextCountdown: Int? = null,
    season: Int? = null,
    episode: Int? = null,
    voiceover: String? = null,
    playFocusRequester: FocusRequester = FocusRequester(),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0C0C0D).copy(alpha = 0.6f),
                        Color.Transparent,
                        Color(0xFF0C0C0D).copy(alpha = 0.8f)
                    )
                )
            )
            .padding(horizontal = 64.dp, vertical = 48.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopSection(title, season, episode, voiceover)
            Spacer(modifier = Modifier.weight(1f))
            BottomSection(
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                isPlaying = isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onSeekBackward = onSeekBackward,
                onSeekForward = onSeekForward,
                onSeek = onSeek,
                onShowAudioMenu = onShowAudioMenu,
                onShowSubtitleMenu = onShowSubtitleMenu,
                onShowQualityMenu = onShowQualityMenu,
                onPreviousEpisode = onPreviousEpisode,
                onNextEpisode = onNextEpisode,
                hasPreviousEpisode = hasPreviousEpisode,
                hasNextEpisode = hasNextEpisode,
                onToggleCodec = onToggleCodec,
                codecPolicy = codecPolicy,
                nextCountdown = nextCountdown,
                playFocusRequester = playFocusRequester
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopSection(title: String, season: Int?, episode: Int?, voiceover: String? = null) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = 0.5.sp
            ),
            color = Color(0xFFE1E1E1)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            if (season != null && episode != null) {
                Text(
                    text = "S${season} · E${episode}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 1.sp
                    ),
                    color = Color(0xFF6E85B7).copy(alpha = 0.8f)
                )
            }
            
            if (!voiceover.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF6E85B7).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = voiceover.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFF6E85B7)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BottomSection(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long = 0L,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeek: (Float) -> Unit,
    onShowAudioMenu: () -> Unit,
    onShowSubtitleMenu: () -> Unit,
    onShowQualityMenu: () -> Unit,
    onPreviousEpisode: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    hasPreviousEpisode: Boolean = false,
    hasNextEpisode: Boolean = false,
    onToggleCodec: () -> Unit,
    codecPolicy: ua.ukrtv.app.player.CodecPolicy,
    nextCountdown: Int?,
    playFocusRequester: FocusRequester = FocusRequester()
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        if (nextCountdown != null) {
            Text(
                text = "НАСТУПНА СЕРІЯ ЧЕРЕЗ $nextCountdown",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 1.5.sp
                ),
                color = Color(0xFF6E85B7),
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF7A7A7A)
            )
            
            TvProgressBar(
                progress = progress,
                bufferedProgress = if (durationMs > 0) bufferedPositionMs.toFloat() / durationMs.toFloat() else 0f,
                durationMs = durationMs,
                onSeekRequested = { seekPosition ->
                    onSeek(seekPosition.toFloat() / durationMs.toFloat())
                },
                onSeek = { ratio ->
                    onSeek(ratio)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .height(4.dp)
            )

            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF7A7A7A)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayPauseButton(
                isPlaying = isPlaying,
                onClick = onPlayPauseToggle,
                focusRequester = playFocusRequester,
                modifier = Modifier.size(56.dp)
            )

            TvPlayerButton(
                icon = Icons.Default.Replay10,
                contentDescription = "Назад 10 секунд",
                onClick = onSeekBackward
            )
            
            TvPlayerButton(
                icon = Icons.Default.Forward10,
                contentDescription = "Вперед 10 секунд",
                onClick = onSeekForward
            )

            if (hasPreviousEpisode || hasNextEpisode) {
                Spacer(modifier = Modifier.width(24.dp))

                if (hasPreviousEpisode) {
                    TvPlayerButton(
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = "Попередня серія",
                        onClick = onPreviousEpisode
                    )
                }
                if (hasNextEpisode) {
                    TvPlayerButton(
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Наступна серія",
                        onClick = onNextEpisode
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            TvPlayerButton(
                icon = Icons.Default.Settings,
                contentDescription = "Якість",
                onClick = onShowQualityMenu
            )
            TvPlayerButton(
                icon = Icons.Default.Audiotrack,
                contentDescription = "Аудіо",
                onClick = onShowAudioMenu
            )
            TvPlayerButton(
                icon = Icons.Default.Subtitles,
                contentDescription = "Субтитри",
                onClick = onShowSubtitleMenu
            )

            TvPlayerButton(
                icon = Icons.Default.Dvr,
                contentDescription = "Виправити чорний екран",
                onClick = onToggleCodec,
                tint = if (codecPolicy == ua.ukrtv.app.player.CodecPolicy.SOFTWARE_FIRST) Color(0xFF6E85B7) else null
            )
        }
    }
}

@Composable
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester = FocusRequester(),
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF6E85B7),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFFE1E1E1),
            focusedContentColor = Color(0xFF0C0C0D)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        modifier = modifier.focusRequester(focusRequester)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Відтворити",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPlayerButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    tint: Color? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = tint ?: Color(0xFFE1E1E1).copy(alpha = 0.8f),
            focusedContainerColor = Color(0xFFE1E1E1),
            focusedContentColor = Color(0xFF0C0C0D)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(width = 2.dp, color = Color(0xFF6E85B7)))
        ),
        modifier = modifier.size(size)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun TvProgressBar(
    progress: Float,
    bufferedProgress: Float = 0f,
    onSeek: (Float) -> Unit,
    durationMs: Long = 0L,
    onSeekRequested: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val safeBuffered = bufferedProgress.coerceIn(0f, 1f)
    val dragModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures { change, dragAmount ->
            change.consume()
            val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
            onSeek(ratio)
            if (dragAmount == 0f && onSeekRequested != null && durationMs > 0) {
                onSeekRequested((ratio * durationMs).toLong())
            }
        }
    }

    Box(
        modifier = modifier
            .then(dragModifier),
        contentAlignment = Alignment.Center
    ) {
        LinearProgressIndicator(
            progress = { safeBuffered },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = Color(0xFF6E85B7).copy(alpha = 0.35f),
            trackColor = Color.Gray.copy(alpha = 0.2f)
        )
        LinearProgressIndicator(
            progress = { safeProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = BrandBlue,
            trackColor = Color.Transparent
        )
    }
}
