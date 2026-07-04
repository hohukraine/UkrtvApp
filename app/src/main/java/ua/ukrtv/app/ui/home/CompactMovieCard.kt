package ua.ukrtv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.FocusDefaults
import ua.ukrtv.app.ui.theme.Gold
import ua.ukrtv.app.ui.theme.Shapes

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CompactMovieCard(
    movie: Movie,
    brandColor: Color = Color(0xFF6E85B7),
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val imageRequest = remember(movie.poster) {
        ImageRequest.Builder(context)
            .data(movie.poster)
            .size(130, 195)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .crossfade(false)
            .build()
    }

    val cardShape = remember { Shapes.cardCompact }

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(CardDefaults.compactWidth)
            .height(CardDefaults.compactHeight),
        shape = ClickableSurfaceDefaults.shape(cardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = FocusDefaults.compactCardScale),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(FocusDefaults.borderWidthThick, brandColor),
                shape = cardShape
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1A1A1A),
            focusedContainerColor = Color(0xFF2A2A2A)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape),
                placeholder = ColorPainter(Color(0xFF1A1A1A)),
                error = ColorPainter(Color(0xFF2A2A2A))
            )

            val badge = movie.quality?.let { q ->
                when {
                    q.contains("4K", ignoreCase = true) -> Badge(q, Gold)
                    q.contains("FHD", ignoreCase = true) || q.contains("FULLHD", ignoreCase = true) -> Badge(q, Color(0xFF6E85B7))
                    else -> null
                }
            } ?: movie.contentType?.let { Badge(it, Color(0xFF4A6FA5)) }

            if (badge != null) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.TopEnd)
                        .background(badge.color.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge.label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

private data class Badge(val label: String, val color: Color)
