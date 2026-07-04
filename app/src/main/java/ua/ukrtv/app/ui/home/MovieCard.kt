package ua.ukrtv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.Shapes

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieCard(
    movie: Movie,
    brandColor: Color = Color(0xFF6E85B7),
    width: Dp = CardDefaults.posterWidth,
    height: Dp = CardDefaults.posterHeight,
    isExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val imageRequest = remember(movie.poster) {
        ImageRequest.Builder(context)
            .data(movie.poster)
            .size(320, 480)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .build()
    }

    val cardShape = remember { Shapes.card }

    Column(
        modifier = modifier.width(width)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .onKeyEvent { event ->
                    if (onDismiss != null) {
                        val isCenter = event.key == Key.DirectionCenter || event.key == Key.Enter
                        val isMenu = event.key == Key.Menu || event.key == Key.Settings
                        
                        if (isCenter) {
                            val isLongPress = event.nativeKeyEvent.flags and android.view.KeyEvent.FLAG_LONG_PRESS != 0
                            if (isLongPress && event.type == KeyEventType.KeyDown) {
                                onDismiss()
                                return@onKeyEvent true
                            }
                        } else if (isMenu && event.type == KeyEventType.KeyUp) {
                            onDismiss()
                            return@onKeyEvent true
                        }
                    }
                    false
                },
            shape = ClickableSurfaceDefaults.shape(cardShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)),
                    shape = cardShape
                )
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF141414),
                focusedContainerColor = Color(0xFF141414)
            )
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = ColorPainter(Color(0xFF141414)),
                error = ColorPainter(Color(0xFF141414))
            )
        }

        Spacer(Modifier.height(12.dp))

        // Metadata Below Poster (Exact Streamverse Style)
        Text(
            text = movie.title.uppercase(),
            color = OnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.5.sp
        )
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (movie.year != null) {
                Text(
                    text = movie.year.toString(),
                    color = OnSurface.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = "Movie", // Placeholder for genre/type
                color = OnSurface.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.weight(1f))
            
            if (!movie.rating.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "★",
                        color = Color(0xFFDAA520),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = movie.rating,
                        color = OnSurface.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
