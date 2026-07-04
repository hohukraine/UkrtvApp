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
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.Shapes

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContinueWatchingCard(
    movie: Movie,
    brandColor: Color = Color(0xFF6E85B7),
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val imageRequest = remember(movie.poster) {
        ImageRequest.Builder(context)
            .data(movie.poster)
            .size(400, 225)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .build()
    }

    val cardShape = remember { Shapes.cardWide }

    val episodeLabel = remember(movie.season, movie.episode) {
        if (movie.season != null && movie.episode != null) {
            "S${movie.season} E${movie.episode}"
        } else null
    }

    Column(
        modifier = modifier.width(CardDefaults.wideWidth)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(CardDefaults.wideHeight)
                .onKeyEvent { event ->
                    if (onDismiss != null) {
                        val isCenter = event.key == Key.DirectionCenter || event.key == Key.Enter
                        val isMenu = event.key == Key.Menu || event.key == Key.Settings
                        val isDelete = event.key == Key.MediaSkipBackward // Use some other key as fallback

                        if (isCenter) {
                            val isLongPress = event.nativeKeyEvent.flags and android.view.KeyEvent.FLAG_LONG_PRESS != 0
                            if (isLongPress && event.type == KeyEventType.KeyDown) {
                                onDismiss()
                                return@onKeyEvent true
                            }
                        } else if ((isMenu || isDelete) && event.type == KeyEventType.KeyUp) {
                            onDismiss()
                            return@onKeyEvent true
                        }
                    }
                    false
                },
            shape = ClickableSurfaceDefaults.shape(cardShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(2.dp, brandColor),
                    shape = cardShape
                )
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF141414),
                focusedContainerColor = Color(0xFF141414)
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = ColorPainter(Color(0xFF141414)),
                    error = ColorPainter(Color(0xFF141414))
                )

                // Progress Bar - Professional Thin Red Line
                if (movie.watchProgress != null && movie.watchProgress > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(movie.watchProgress.toFloat() / 100f)
                                .fillMaxHeight()
                                .background(Color.Red)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Title and Episode Metadata (Streamverse Style)
        Text(
            text = movie.title.uppercase(),
            color = OnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.5.sp
        )
        if (episodeLabel != null) {
            Text(
                text = episodeLabel,
                color = brandColor.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}
