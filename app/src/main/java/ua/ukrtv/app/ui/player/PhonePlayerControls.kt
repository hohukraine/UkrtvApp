package ua.ukrtv.app.ui.player

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.ukrtv.app.ui.theme.BrandBlue

@Composable
fun PhonePlayerControls(
    title: String,
    currentPosition: Long,
    duration: Long,
    seekProgress: Float,
    isPlaying: Boolean,
    isSeeking: Boolean,
    showSeekOverlay: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    brandColor: Color = BrandBlue,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: (Long) -> Unit,
    onSeekDrag: (Float) -> Unit,
    onRotate: () -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current as? Activity
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                if (hasNext) {
                    IconButton(onClick = {
                        onNextEpisode()
                        onInteract()
                    }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Наступна серія", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
                IconButton(
                    onClick = { onRotate() }
                ) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Повернути екран", tint = Color.White)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                if (hasPrevious) {
                    IconButton(onClick = {
                        onPreviousEpisode()
                        onInteract()
                    }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Попередня серія", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }

                IconButton(onClick = {
                    onSeekTo(maxOf(0L, currentPosition - 10_000L))
                    onInteract()
                }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Replay10, contentDescription = "-10с", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable {
                            onTogglePlay()
                            onInteract()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Відтворити",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = {
                    onSeekTo(minOf(duration, currentPosition + 10_000L))
                    onInteract()
                }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Forward10, contentDescription = "+10с", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                if (hasNext) {
                    IconButton(onClick = {
                        onNextEpisode()
                        onInteract()
                    }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Наступна серія", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                onSeekStart()
                                onInteract()
                            },
                            onDragEnd = {
                                val targetMs = (seekProgress * duration).toLong()
                                onSeekEnd(targetMs)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val progress = (change.position.x / size.width).coerceIn(0f, 1f)
                                onSeekDrag(progress)
                                onInteract()
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height
                val barHeight = 3.dp.toPx()
                val barY = (h - barHeight) / 2f
                val corner = CornerRadius(barHeight / 2)

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.2f),
                    topLeft = Offset(0f, barY),
                    size = Size(w, barHeight),
                    cornerRadius = corner
                )

                val progressWidth = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
                if (progressWidth > 0f) {
                    drawRoundRect(
                        color = brandColor,
                        topLeft = Offset(0f, barY),
                        size = Size(w * progressWidth, barHeight),
                        cornerRadius = corner
                    )
                }

                val thumbX = (w * progressWidth).coerceIn(0f, w)
                val thumbRadius = if (isSeeking) 8.dp.toPx() else 5.dp.toPx()
                drawCircle(
                    color = brandColor,
                    radius = thumbRadius,
                    center = Offset(thumbX, h / 2f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = playerFormatTime(currentPosition),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    text = playerFormatTime(duration),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
