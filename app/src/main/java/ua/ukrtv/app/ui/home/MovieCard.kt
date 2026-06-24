package ua.ukrtv.app.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import ua.ukrtv.app.domain.model.Movie

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    loadDelayMs: Int = 0
) {
    var isFocused by remember { mutableStateOf(false) }
    var showImage by remember { mutableStateOf(loadDelayMs == 0) }

    LaunchedEffect(loadDelayMs) {
        if (loadDelayMs > 0) {
            delay(loadDelayMs.toLong())
            showImage = true
        }
    }

    val context = LocalContext.current
    val imageRequest = remember(movie.poster) {
        ImageRequest.Builder(context)
            .data(movie.poster)
            .size(180, 270)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .crossfade(false)
            .build()
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardScale"
    )

    val cardShape = remember { RoundedCornerShape(8.dp) }
    val cardColors = ClickableSurfaceDefaults.colors(
        containerColor = Color(0xFF1A1A1A),
        focusedContainerColor = Color(0xFF2A2A2A)
    )
    val cardBorder = ClickableSurfaceDefaults.border(
        focusedBorder = Border(
            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)),
            shape = cardShape
        )
    )
    
    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
        )
    }

    val alpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.8f,
        label = "cardAlpha"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(160.dp)
            .height(240.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (onDismiss != null && isFocused &&
                    event.type == KeyEventType.KeyUp &&
                    event.key == Key.MediaSkipBackward
                ) {
                    onDismiss()
                    true
                } else false
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = ClickableSurfaceDefaults.shape(cardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = cardBorder,
        colors = cardColors
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showImage) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.alpha = alpha },
                    placeholder = ColorPainter(Color(0xFF1A1A1A)),
                    error = ColorPainter(Color(0xFF2A2A2A))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A1A))
                )
            }

            // Rating Badge
            if (!movie.rating.isNullOrEmpty()) {
                Surface(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFFDAA520).copy(alpha = 0.9f)
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                    onClick = {}
                ) {
                    Text(
                        text = movie.rating,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(gradientBrush)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = movie.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )

                if (movie.watchProgress != null && movie.watchProgress > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(movie.watchProgress.toFloat() / 100f)
                                .fillMaxHeight()
                                .background(Color.Red, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}
