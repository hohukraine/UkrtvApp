package ua.ukrtv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import ua.ukrtv.app.domain.model.Movie

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val imageRequest = remember(movie.poster) {
        ImageRequest.Builder(context)
            .data(movie.poster)
            .crossfade(true)
            .build()
    }

    val cardShape = remember { RoundedCornerShape(4.dp) }
    val cardScale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
    val cardColors = ClickableSurfaceDefaults.colors(
        containerColor = Color(0xFF1A1A1A),
        focusedContainerColor = Color(0xFF1A1A1A)
    )
    val cardBorder = ClickableSurfaceDefaults.border(
        focusedBorder = Border(
            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
            shape = cardShape
        )
    )
    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color(0xAA000000))
        )
    }
    val progressBgColor = remember { Color.White.copy(alpha = 0.2f) }
    val progressShape = remember { RoundedCornerShape(2.dp) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(180.dp)
            .height(270.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(cardShape),
        scale = cardScale,
        border = cardBorder,
        colors = cardColors
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = ColorPainter(Color(0xFF1A1A1A)),
                error = ColorPainter(Color(0xFF2A2A2A))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .align(Alignment.BottomCenter)
                    .background(gradientBrush)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = movie.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                if (movie.watchProgress != null && movie.watchProgress > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(progressBgColor, progressShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(movie.watchProgress.toFloat() / 100f)
                                .fillMaxHeight()
                                .background(Color.Red, progressShape)
                        )
                    }
                }
            }
        }
    }
}
