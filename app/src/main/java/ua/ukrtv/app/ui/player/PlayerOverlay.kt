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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onPlayPauseToggle: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onShowAudioMenu: () -> Unit,
    onShowSubtitleMenu: () -> Unit,
    onShowQualityMenu: () -> Unit,
    nextCountdown: Int? = null,
    season: Int? = null,
    episode: Int? = null,
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
            TopSection(title, season, episode)
            Spacer(modifier = Modifier.weight(1f))
            BottomSection(
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onSeekBackward = onSeekBackward,
                onSeekForward = onSeekForward,
                onShowAudioMenu = onShowAudioMenu,
                onShowSubtitleMenu = onShowSubtitleMenu,
                onShowQualityMenu = onShowQualityMenu,
                nextCountdown = nextCountdown
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopSection(title: String, season: Int?, episode: Int?) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = 0.5.sp
            ),
            color = Color(0xFFE1E1E1)
        )
        if (season != null && episode != null) {
            Text(
                text = "S${season} · E${episode}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 1.sp
                ),
                color = Color(0xFF6E85B7).copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BottomSection(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onShowAudioMenu: () -> Unit,
    onShowSubtitleMenu: () -> Unit,
    onShowQualityMenu: () -> Unit,
    nextCountdown: Int?
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
                onSeek = { ratio ->
                    // Logic handled in parent or here
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
        modifier = modifier
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
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFFE1E1E1).copy(alpha = 0.8f),
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
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val dragModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures { change, _ ->
            val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
            onSeek(ratio)
        }
    }

    Box(
        modifier = modifier
            .then(dragModifier),
        contentAlignment = Alignment.Center
    ) {
        LinearProgressIndicator(
            progress = { safeProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = BrandBlue,
            trackColor = Color.Gray.copy(alpha = 0.5f)
        )
    }
}
