package ua.ukrtv.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.OnSurfaceVariant
import ua.ukrtv.app.ui.theme.Scrim
import ua.ukrtv.app.ui.theme.Shapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.ui.theme.deviceImage
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious


import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerOverlay(
    visible: Boolean,
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long = 0L,
    showSkipIntro: Boolean = false,
    onSkipIntro: () -> Unit = {},
    onPlayPauseToggle: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeek: (Float) -> Unit,
    hasEpisodes: Boolean = false,
    hasNextEpisode: Boolean = false,
    hasPreviousEpisode: Boolean = false,
    onNextEpisode: () -> Unit = {},
    onPreviousEpisode: () -> Unit = {},
    nextCountdown: Int? = null,
    countdownEpisode: Episode? = null,
    countdownSeason: Int? = null,
    season: Int? = null,
    episode: Int? = null,
    showSeasonEpisode: Boolean = true,
    voiceover: String? = null,
    pickerColumns: List<PickerColumn> = emptyList(),
    pickerFocusedIndex: Int = 0,
    onPickerColumnFocused: (Int) -> Unit = {},
    onPickerValueChange: (Int) -> Unit = {},
    onPickerCommit: () -> Unit = {},
    brandColor: Color = BrandBlue,
    playFocusRequester: FocusRequester = FocusRequester(),
    heldSeekDir: SeekDirection? = null,
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

    var seekDirection by remember { mutableStateOf<SeekDirection?>(null) }

    LaunchedEffect(seekDirection) {
        val dir = seekDirection ?: return@LaunchedEffect
        delay(600)
        seekDirection = null
    }

    fun onSeekWithIndicator(forward: Boolean) {
        seekDirection = if (forward) SeekDirection.Forward else SeekDirection.Backward
        if (forward) onSeekForward() else onSeekBackward()
    }

    Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
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
                    .height(120.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Scrim)
                        )
                    )
            )

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(200, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(200, easing = FastOutSlowInEasing))
            ) {
                SeekIndicator(
                    brandColor = brandColor,
                    direction = seekDirection,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) + slideInVertically(tween(200, easing = FastOutSlowInEasing), initialOffsetY = { -it }),
                exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing), targetOffsetY = { -it })
            ) {
                PlayerOverlayTitle(
                    brandColor = brandColor,
                    title = title,
                    season = season,
                    episode = episode,
                    showSeasonEpisode = showSeasonEpisode,
                    voiceover = voiceover,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }

            if (showSkipIntro) {

            if (heldSeekDir != null) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(150, easing = LinearEasing)),
                    exit = fadeOut(tween(150, easing = LinearEasing))
                ) {
                    HeldSeekProgress(
                        brandColor = brandColor,
                        direction = heldSeekDir,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) + slideInVertically(tween(200, easing = FastOutSlowInEasing), initialOffsetY = { -it }),
                    exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing), targetOffsetY = { -it })
                ) {
                    SkipIntroButton(
                        brandColor = brandColor,
                        onClick = onSkipIntro,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 28.dp, end = 64.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) + slideInVertically(tween(200, easing = FastOutSlowInEasing), initialOffsetY = { it }),
                exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing), targetOffsetY = { it }),
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
                    hasEpisodes = hasEpisodes,
                    hasNextEpisode = hasNextEpisode,
                    hasPreviousEpisode = hasPreviousEpisode,
                    onNextEpisode = onNextEpisode,
                    onPreviousEpisode = onPreviousEpisode,
                    playFocusRequester = playFocusRequester,
                    onPlayPauseToggle = onPlayPauseToggle,
                    onSeekBackward = { onSeekWithIndicator(false) },
                    onSeekForward = { onSeekWithIndicator(true) },
                    pickerColumns = pickerColumns,
                    pickerFocusedIndex = pickerFocusedIndex,
                    onPickerColumnFocused = onPickerColumnFocused,
                    onPickerValueChange = onPickerValueChange,
                    onPickerCommit = onPickerCommit
                )
            }
        }
    }

enum class SeekDirection { Forward, Backward }

@Composable
private fun PlayerOverlayTitle(
    brandColor: Color,
    title: String,
    season: Int?,
    episode: Int?,
    showSeasonEpisode: Boolean = true,
    voiceover: String?,
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
        if (showSeasonEpisode && season != null && episode != null || !voiceover.isNullOrEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                if (showSeasonEpisode && season != null && episode != null) {
                    SeasonEpisodeBadge(season = season, episode = episode, brandColor = brandColor)
                }
                if (!voiceover.isNullOrEmpty()) {
                    VoiceoverBadge(voiceover = voiceover, brandColor = brandColor)
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
            .background(brandColor.copy(alpha = 0.15f), Shapes.badge)
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
    hasEpisodes: Boolean,
    hasNextEpisode: Boolean,
    hasPreviousEpisode: Boolean,
    onNextEpisode: () -> Unit,
    onPreviousEpisode: () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayPauseButton(
                brandColor = brandColor,
                isPlaying = isPlaying,
                onClick = onPlayPauseToggle,
                focusRequester = playFocusRequester,
                modifier = Modifier.size(56.dp)
            )

            if (hasEpisodes && hasNextEpisode) {
                NetflixButton(
                    brandColor = brandColor,
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Наступна серія",
                    onClick = onNextEpisode
                )
            } else {
                NetflixButton(
                    brandColor = brandColor,
                    icon = Icons.Default.Replay10,
                    contentDescription = "Назад 10 секунд",
                    onClick = onSeekBackward
                )

                NetflixButton(
                    brandColor = brandColor,
                    icon = Icons.Default.Forward10,
                    contentDescription = "Вперед 10 секунд",
                    onClick = onSeekForward
                )
            }

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

        Spacer(modifier = Modifier.height(20.dp))

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
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        if (episode != null) {
            EpisodePoster(poster = episode.poster, number = episode.number, title = episode.title, brandColor = brandColor)
        } else {
            Text(
                text = "НАСТУПНА СЕРІЯ ЧЕРЕЗ $countdown",
                color = brandColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
private fun EpisodePoster(poster: String, number: Int, title: String, brandColor: Color) {
    val deviceClass = LocalDeviceClass.current
    val isMediatek = LocalIsMediatek.current
    if (poster.isNotEmpty()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(poster)
                .size(80, 120)
                .deviceImage(deviceClass, isMediatek)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(width = 50.dp, height = 75.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(Color(0xFF1A1A1A))
        )
    }
    Column {
        Text(
            text = "Серія $number",
            color = brandColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        if (title.isNotEmpty()) {
            Text(
                text = title,
                color = Color(0xFFE1E1E1).copy(alpha = 0.7f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SkipIntroButton(
    brandColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Scrim,
            focusedContainerColor = brandColor,
            contentColor = OnSurface,
            focusedContentColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "ПРОПУСТИТИ ВСТУП",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = ">>",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6E85B7)
            )
        }
    }
}

@Composable
private fun SeekIndicator(
    brandColor: Color,
    direction: SeekDirection?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = direction != null,
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
                text = when (direction) {
                    SeekDirection.Forward -> "+10"
                    SeekDirection.Backward -> "-10"
                    null -> ""
                },
                color = brandColor,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun HeldSeekProgress(
    brandColor: Color,
    direction: SeekDirection,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val seekStep = SEEK_STEP_MS
    val targetMs = when (direction) {
        SeekDirection.Forward -> (positionMs + seekStep).coerceAtMost(durationMs)
        SeekDirection.Backward -> (positionMs - seekStep).coerceAtLeast(0L)
    }
    val targetProgress = if (durationMs > 0) targetMs.toFloat() / durationMs.toFloat() else 0f

    Box(
        modifier = modifier
            .background(Color(0x66000000), RoundedCornerShape(16.dp))
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (direction) {
                    SeekDirection.Forward -> "+10"
                    SeekDirection.Backward -> "-10"
                },
                color = brandColor,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(12.dp))
            Canvas(
                modifier = Modifier
                    .width(200.dp)
                    .height(6.dp)
            ) {
                val w = size.width
                val h = size.height
                val barHeight = 4.dp.toPx()
                val barY = (h - barHeight) / 2f
                val corner = CornerRadius(barHeight / 2)

                drawRoundRect(
                    color = Color.Gray.copy(alpha = 0.4f),
                    topLeft = Offset(0f, barY),
                    size = Size(w, barHeight),
                    cornerRadius = corner
                )
                val currentProgress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.6f),
                    topLeft = Offset(0f, barY),
                    size = Size(w * currentProgress.coerceIn(0f, 1f), barHeight),
                    cornerRadius = corner
                )
                drawRoundRect(
                    color = brandColor,
                    topLeft = Offset(0f, barY),
                    size = Size(w * targetProgress.coerceIn(0f, 1f), barHeight),
                    cornerRadius = corner
                )
                val thumbX = (w * targetProgress.coerceIn(0f, 1f))
                val thumbRadius = 5.dp.toPx()
                drawCircle(
                    color = brandColor,
                    radius = thumbRadius,
                    center = Offset(thumbX, h / 2f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${formatTime(positionMs)} → ${formatTime(targetMs)}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
        }
    }
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
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = brandColor,
            contentColor = Color.White,
            focusedContainerColor = OnSurface,
            focusedContentColor = Background
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
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = OnSurface.copy(alpha = 0.8f),
            focusedContainerColor = OnSurface,
            focusedContentColor = Background
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(width = 2.dp, color = brandColor)
            )
        ),
        modifier = modifier.size(48.dp)
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
