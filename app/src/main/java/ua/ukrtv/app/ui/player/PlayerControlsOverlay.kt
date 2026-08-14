package ua.ukrtv.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.OnSurfaceVariant
import ua.ukrtv.app.ui.theme.Scrim
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerControlsOverlay(
    visible: Boolean,
    title: String,
    season: Int? = null,
    episode: Int? = null,
    currentVoiceover: String? = null,
    stats: String = "",
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long = 0L,
    brandColor: Color = BrandBlue,
    hasNextEpisode: Boolean = false,
    nextCountdown: Int? = null,
    countdownEpisode: Episode? = null,
    pickerColumns: List<PickerColumn> = emptyList(),
    pickerFocusedIndex: Int = 0,
    onPlayPauseToggle: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onNextEpisode: () -> Unit,
    onPickerColumnFocused: (Int) -> Unit,
    onPickerValueChange: (Int) -> Unit,
    onPickerCommit: () -> Unit,
    playFocusRequester: FocusRequester = FocusRequester(),
    modifier: Modifier = Modifier
) {
    val progress by remember(positionMs, durationMs) {
        derivedStateOf {
            if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
        }
    }
    val bufferedProgress by remember(bufferedPositionMs, durationMs) {
        derivedStateOf {
            if (durationMs > 0) bufferedPositionMs.toFloat() / durationMs.toFloat() else 0f
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ControlsTitle(
            brandColor = brandColor,
            title = title,
            season = season,
            episode = episode,
            currentVoiceover = currentVoiceover,
            modifier = Modifier.align(Alignment.TopStart)
        )

        if (stats.isNotBlank()) {
            Text(
                text = stats,
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 64.dp)
                    .widthIn(max = 480.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Scrim, Color.Transparent)
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Scrim)
                    )
                )
        )

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300, easing = LinearEasing)) + slideInVertically(tween(300, easing = LinearEasing), initialOffsetY = { it }),
            exit = fadeOut(tween(300, easing = LinearEasing)) + slideOutVertically(tween(300, easing = LinearEasing), targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomControls(
                brandColor = brandColor,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                progress = progress,
                bufferedProgress = bufferedProgress,
                nextCountdown = nextCountdown,
                countdownEpisode = countdownEpisode,
                hasNextEpisode = hasNextEpisode,
                onNextEpisode = onNextEpisode,
                playFocusRequester = playFocusRequester,
                onPlayPauseToggle = onPlayPauseToggle,
                onSeekBackward = onSeekBackward,
                onSeekForward = onSeekForward,
                pickerColumns = pickerColumns,
                pickerFocusedIndex = pickerFocusedIndex,
                onPickerColumnFocused = onPickerColumnFocused,
                onPickerValueChange = onPickerValueChange,
                onPickerCommit = onPickerCommit
            )
        }
    }
}

@Composable
private fun ControlsTitle(
    brandColor: Color,
    title: String,
    season: Int?,
    episode: Int?,
    currentVoiceover: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(start = 64.dp, top = 28.dp)
    ) {
        Text(
            text = title,
            color = OnSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (season != null && episode != null || !currentVoiceover.isNullOrEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                if (season != null && episode != null) {
                    SeasonEpisodeBadge(season = season, episode = episode, brandColor = brandColor)
                }
                if (!currentVoiceover.isNullOrEmpty()) {
                    VoiceoverBadge(voiceover = currentVoiceover, brandColor = brandColor)
                }
            }
        }
    }
}

@Composable
private fun SeasonEpisodeBadge(season: Int, episode: Int, brandColor: Color) {
    Text(
        text = "S$season · E$episode",
        color = brandColor.copy(alpha = 0.8f),
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 1.sp
    )
}

@Composable
private fun VoiceoverBadge(voiceover: String, brandColor: Color) {
    Box(
        modifier = Modifier
            .background(brandColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = voiceover.uppercase(),
            color = brandColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BottomControls(
    brandColor: Color,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    progress: Float,
    bufferedProgress: Float,
    nextCountdown: Int?,
    countdownEpisode: Episode?,
    hasNextEpisode: Boolean,
    onNextEpisode: () -> Unit,
    playFocusRequester: FocusRequester,
    onPlayPauseToggle: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    pickerColumns: List<PickerColumn> = emptyList(),
    pickerFocusedIndex: Int = 0,
    onPickerColumnFocused: (Int) -> Unit = {},
    onPickerValueChange: (Int) -> Unit = {},
    onPickerCommit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 64.dp, end = 64.dp)
            .padding(bottom = 32.dp)
    ) {
        if (nextCountdown != null) {
            NextEpisodeCountdown(
                brandColor = brandColor,
                countdown = nextCountdown,
                episode = countdownEpisode
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayPauseButton(
                brandColor = brandColor,
                isPlaying = isPlaying,
                onClick = onPlayPauseToggle,
                focusRequester = playFocusRequester,
                modifier = Modifier.size(52.dp)
            )

            if (hasNextEpisode) {
                NetflixButton(
                    brandColor = brandColor,
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Наступна серія",
                    onClick = onNextEpisode,
                    modifier = Modifier.size(44.dp)
                )
            }

            NetflixButton(
                brandColor = brandColor,
                icon = Icons.Default.Replay10,
                contentDescription = "Назад 10 секунд",
                onClick = onSeekBackward,
                modifier = Modifier.size(44.dp)
            )

            NetflixButton(
                brandColor = brandColor,
                icon = Icons.Default.Forward10,
                contentDescription = "Вперед 10 секунд",
                onClick = onSeekForward,
                modifier = Modifier.size(44.dp)
            )

            if (pickerColumns.isNotEmpty()) {
                PlayerPickerRow(
                    columns = pickerColumns,
                    focusedIndex = pickerFocusedIndex,
                    brandColor = brandColor,
                    onColumnFocused = onPickerColumnFocused,
                    onValueChange = onPickerValueChange,
                    onCommit = onPickerCommit
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        NetflixProgressBar(
            brandColor = brandColor,
            progress = progress,
            bufferedProgress = bufferedProgress
        )

        Spacer(modifier = Modifier.height(8.dp))

        TimeLabelsRow(
            positionMs = positionMs,
            durationMs = durationMs
        )
    }
}

@Composable
private fun NextEpisodeCountdown(
    brandColor: Color,
    countdown: Int,
    episode: Episode?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        if (episode != null && episode.poster.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(episode.poster)
                    .size(80, 120)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 44.dp, height = 66.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Color(0xFF1A1A1A))
            )
        }
        Text(
            text = "Серія ${episode?.number ?: ""}${episode?.title?.let { ": $it" } ?: ""}",
            color = brandColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = "через $countdown",
            color = Color(0xFFE1E1E1).copy(alpha = 0.7f),
            fontSize = 13.sp
        )
    }
}

@Composable
internal fun PlayerSeekIndicator(
    brandColor: Color,
    deltaMs: Long,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = deltaMs != 0L,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(400))
    ) {
        Box(
            modifier = modifier
                .background(Color(0x66000000), RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatSeekDelta(deltaMs),
                color = brandColor,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

private fun formatSeekDelta(ms: Long): String {
    val sign = if (ms >= 0) "+" else "-"
    val totalSeconds = kotlin.math.abs(ms) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "$sign$minutes:${seconds.toString().padStart(2, '0')}"
    else "$sign$totalSeconds"
}

@Composable
private fun TimeLabelsRow(positionMs: Long, durationMs: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatTime(positionMs),
            color = OnSurfaceVariant,
            fontSize = 12.sp
        )
        Text(
            text = formatTime(durationMs),
            color = OnSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun NetflixProgressBar(
    brandColor: Color,
    progress: Float,
    bufferedProgress: Float
) {
    val bufferedWidth = bufferedProgress.coerceIn(0f, 1f)
    val progressWidth = progress.coerceIn(0f, 1f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val w = size.width
        val h = size.height
        val barHeight = 3.dp.toPx()
        val barY = (h - barHeight) / 2f
        val corner = CornerRadius(barHeight / 2)

        drawRoundRect(
            color = Color.Gray.copy(alpha = 0.3f),
            topLeft = Offset(0f, barY),
            size = Size(w, barHeight),
            cornerRadius = corner
        )
        if (bufferedWidth > 0f) {
            drawRoundRect(
                color = brandColor.copy(alpha = 0.35f),
                topLeft = Offset(0f, barY),
                size = Size(w * bufferedWidth, barHeight),
                cornerRadius = corner
            )
        }
        if (progressWidth > 0f) {
            drawRoundRect(
                color = brandColor,
                topLeft = Offset(0f, barY),
                size = Size(w * progressWidth, barHeight),
                cornerRadius = corner
            )
        }

        val thumbX = (w * progressWidth).coerceIn(0f, w)
        val thumbRadius = 6.dp.toPx()
        drawCircle(
            color = brandColor,
            radius = thumbRadius,
            center = Offset(thumbX, h / 2f)
        )
        drawCircle(
            color = Color.White,
            radius = thumbRadius * 0.6f,
            center = Offset(thumbX, h / 2f)
        )
    }
}

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
    brandColor: Color,
    isPlaying: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester = FocusRequester(),
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(26.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = brandColor,
            contentColor = Color.White,
            focusedContainerColor = OnSurface,
            focusedContentColor = Color.Black
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
private fun NetflixButton(
    brandColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = OnSurface.copy(alpha = 0.8f),
            focusedContainerColor = OnSurface,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, brandColor)
            )
        ),
        modifier = modifier
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
