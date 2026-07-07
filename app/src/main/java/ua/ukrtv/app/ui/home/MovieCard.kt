package ua.ukrtv.app.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
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

private val cardShape = RoundedCornerShape(8.dp)
private val focusBorderWidth = 3.dp

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
    onLongClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val actualLongClick = onLongClick ?: onDismiss

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "cardScale"
    )

    val imageRequest = remember(movie.poster) {
        ImageRequest.Builder(context)
            .data(movie.poster)
            .size(180, 270)
            .crossfade(false)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
    }

    val sharedMod = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            val sharedContentState = rememberSharedContentState(key = "poster_${movie.id}")
            Modifier.sharedElement(sharedContentState = sharedContentState, animatedVisibilityScope = animatedVisibilityScope)
        }
    } else Modifier

    Surface(
        onClick = onClick,
        onLongClick = actualLongClick,
        interactionSource = interactionSource,
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(sharedMod)
            .onKeyEvent { event ->
                if (onDismiss != null) {
                    val isMenu = event.key == Key.Menu || event.key == Key.Settings

                    if (isMenu && event.type == KeyEventType.KeyUp) {
                        onDismiss()
                        return@onKeyEvent true
                    }
                }
                false
            },
        shape = ClickableSurfaceDefaults.shape(cardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(focusBorderWidth, Color.White),
                shape = cardShape
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF141414),
            focusedContainerColor = Color(0xFF141414)
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = ColorPainter(Color(0xFF141414)),
                error = ColorPainter(Color(0xFF141414))
            )

            if (movie.provider != null) {
                val providerColor = when (movie.provider) {
                    "Uakino" -> Color(0xFFFF6B35)
                    "Eneyida" -> Color(0xFF4ECDC4)
                    else -> Color(0xFF888888)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(providerColor.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = movie.provider!!.uppercase(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Normal,
                        letterSpacing = 0.5.sp,
                        maxLines = 1
                    )
                }
            }

            // Semi-transparent bottom bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isExpanded) 90.dp else 72.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 10.dp, bottom = if (isExpanded) 10.dp else 6.dp)
            ) {
                Text(
                    text = movie.title.uppercase(),
                    color = Color.White,
                    fontSize = if (isExpanded) 13.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.3.sp,
                    lineHeight = 14.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    if (movie.year != null) {
                        Text(
                            text = movie.year.toString(),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (!movie.rating.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "★",
                                color = Color(0xFFDAA520),
                                fontSize = 9.sp
                            )
                            Spacer(Modifier.width(1.dp))
                            Text(
                                text = movie.rating,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (!movie.quality.isNullOrEmpty()) {
                        Text(
                            text = movie.quality.uppercase(),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
