package ua.ukrtv.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import android.view.MotionEvent


import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity


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
    deepResolutionPending: Boolean = false,
    onPickerColumnFocused: (Int) -> Unit = {},
    onPickerValueChange: (Int) -> Unit = {},
    onPickerCommit: () -> Unit = {},
    brandColor: Color = BrandBlue,
    playFocusRequester: FocusRequester = FocusRequester(),
    heldSeekDir: SeekDirection? = null,
    heldSeekTarget: Long? = null,
    modifier: Modifier = Modifier
) {
    val progressProvider = remember(positionMs, durationMs) {
        { if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f }
    }
    val bufferedProgressProvider = remember(bufferedPositionMs, durationMs) {
        { if (durationMs > 0) bufferedPositionMs.toFloat() / durationMs.toFloat() else 0f }
    }

    var seekDirection by remember { mutableStateOf<SeekDirection?>(null) }

    LaunchedEffect(seekDirection) {
        val dir = seekDirection ?: return@LaunchedEffect
        delay(600)
        seekDirection = null
    }

    LaunchedEffect(heldSeekDir) {
        if (heldSeekDir != null) seekDirection = heldSeekDir
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

            if (heldSeekTarget != null && heldSeekDir != null) {
                HeldSeekProgress(
                    brandColor = brandColor,
                    direction = heldSeekDir,
                    positionMs = positionMs,
                    targetMs = heldSeekTarget,
                    durationMs = durationMs,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (seekDirection != null) {
                SeekIndicator(
                    brandColor = brandColor,
                    direction = seekDirection,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            AnimatedVisibility(
                visible = nextCountdown != null || countdownEpisode != null,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.fillMaxSize()
            ) {
                NextEpisodeOverlay(
                    brandColor = brandColor,
                    episode = countdownEpisode,
                    season = countdownSeason
                )
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(250, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(250, easing = FastOutSlowInEasing)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PlayerOverlayTitle(
                        brandColor = brandColor,
                        title = title,
                        season = season,
                        episode = episode,
                        showSeasonEpisode = showSeasonEpisode,
                        voiceover = voiceover,
                        modifier = Modifier.align(Alignment.TopStart)
                    )

                    if (showSkipIntro) {
                        SkipIntroButton(
                            brandColor = brandColor,
                            onClick = onSkipIntro,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 28.dp, end = 64.dp)
                        )
                    }

                    BottomControls(
                        brandColor = brandColor,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        progress = progressProvider,
                        bufferedProgress = bufferedProgressProvider,
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
                        deepResolutionPending = deepResolutionPending,
                        onPickerColumnFocused = onPickerColumnFocused,
                        onPickerValueChange = onPickerValueChange,
                        onPickerCommit = onPickerCommit,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
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
    progress: () -> Float,
    bufferedProgress: () -> Float,
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
    deepResolutionPending: Boolean = false,
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
            } else if (deepResolutionPending && hasEpisodes.not()) {
                Text(
                    text = "Завантаження серій...",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

        }

        Spacer(modifier = Modifier.height(20.dp))

        NetflixProgressBar(
            brandColor = brandColor,
            progress = progress,
            bufferedProgress = bufferedProgress,
            durationMs = durationMs
        )

        Spacer(modifier = Modifier.height(8.dp))

        TimeLabelsRow(
            positionMs = positionMs,
            durationMs = durationMs
        )
    }
}

@Composable
private fun NextEpisodeOverlay(
    brandColor: Color,
    episode: Episode?,
    season: Int?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
    ) {
        if (episode != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 64.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(episode.poster)
                        .size(270, 405)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 135.dp, height = 203.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(Color(0xFF1A1A1A))
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "НАСТУПНА СЕРІЯ",
                        color = brandColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp
                    )

                    Text(
                        text = if (season != null) "Сезон $season · Серія ${episode.number}" else "Серія ${episode.number}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )

                    if (episode.title.isNotEmpty()) {
                        Text(
                            text = episode.title,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
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
    val scale by animateFloatAsState(
        targetValue = if (direction != null) 1f else 0.7f,
        animationSpec = tween(200),
        label = "seekScale"
    )

    AnimatedVisibility(
        visible = direction != null,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300))
    ) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(Color(0x66000000), RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = when (direction) {
                        SeekDirection.Forward -> Icons.Default.Forward10
                        SeekDirection.Backward -> Icons.Default.Replay10
                        null -> Icons.Default.Forward10
                    },
                    contentDescription = null,
                    tint = brandColor,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = when (direction) {
                        SeekDirection.Forward -> "+10с"
                        SeekDirection.Backward -> "-10с"
                        null -> ""
                    },
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
private fun HeldSeekProgress(
    brandColor: Color,
    direction: SeekDirection,
    positionMs: Long,
    targetMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val targetProgress = if (durationMs > 0) targetMs.toFloat() / durationMs.toFloat() else 0f

    Box(
        modifier = modifier
            .background(Color(0x66000000), RoundedCornerShape(16.dp))
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val deltaMs = targetMs - positionMs
            val deltaText = if (deltaMs >= 0) "+${deltaMs / 1000}" else "${deltaMs / 1000}"
            Text(
                text = deltaText,
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
    progress: () -> Float,
    bufferedProgress: () -> Float,
    durationMs: Long = 0L
) {
    var isHovered by remember { mutableStateOf(false) }
    var hoverXFraction by remember { mutableStateOf(0f) }
    var barWidth by remember { mutableFloatStateOf(0f) }
    val barHeight by animateFloatAsState(
        targetValue = if (isHovered) 8f else 3f,
        animationSpec = tween(200),
        label = "barHeight"
    )
    val thumbRadius by animateFloatAsState(
        targetValue = if (isHovered) 10f else 6f,
        animationSpec = tween(200),
        label = "thumbRadius"
    )
    val bufferedWidthProvider = remember { { bufferedProgress().coerceIn(0f, 1f) } }
    val progressWidthProvider = remember { { progress().coerceIn(0f, 1f) } }

    Box(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .onSizeChanged { barWidth = it.width.toFloat() }
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_HOVER_ENTER -> {
                            hoverXFraction = (event.x / barWidth).coerceIn(0f, 1f)
                            isHovered = true
                            false
                        }
                        MotionEvent.ACTION_HOVER_MOVE -> {
                            hoverXFraction = (event.x / barWidth).coerceIn(0f, 1f)
                            false
                        }
                        MotionEvent.ACTION_HOVER_EXIT -> {
                            isHovered = false
                            false
                        }
                        else -> false
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val currentBarHeight = barHeight.dp.toPx()
            val barY = (h - currentBarHeight) / 2f
            val corner = CornerRadius(currentBarHeight / 2)

            drawRoundRect(
                color = Color.Gray.copy(alpha = 0.3f),
                topLeft = Offset(0f, barY),
                size = Size(w, currentBarHeight),
                cornerRadius = corner
            )

            val bufferedWidth = bufferedWidthProvider()
            if (bufferedWidth > 0f) {
                drawRoundRect(
                    color = brandColor.copy(alpha = 0.35f),
                    topLeft = Offset(0f, barY),
                    size = Size(w * bufferedWidth, currentBarHeight),
                    cornerRadius = corner
                )
            }

            val progressWidth = progressWidthProvider()
            if (progressWidth > 0f) {
                drawRoundRect(
                    color = brandColor,
                    topLeft = Offset(0f, barY),
                    size = Size(w * progressWidth, currentBarHeight),
                    cornerRadius = corner
                )
            }

            val thumbX = (w * progressWidth).coerceIn(0f, w)
            val currentThumbRadius = thumbRadius.dp.toPx()
            drawCircle(
                color = brandColor,
                radius = currentThumbRadius,
                center = Offset(thumbX, h / 2f)
            )
            drawCircle(
                color = Color.White,
                radius = currentThumbRadius * 0.6f,
                center = Offset(thumbX, h / 2f)
            )
        }

        if (isHovered && durationMs > 0) {
            val hoverTime = (hoverXFraction * durationMs).toLong()
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .offset(
                        x = with(LocalDensity.current) {
                            ((hoverXFraction - 0.5f) * barWidth).toDp()
                        }
                    )
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = formatTime(hoverTime),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
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
