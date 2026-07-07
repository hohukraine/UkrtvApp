package ua.ukrtv.app.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    onDismiss: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "continueCardScale"
    )

    val imageRequest = remember(movie.poster) {
        ImageRequest.Builder(context)
            .data(movie.poster)
            .size(260, 390)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .crossfade(false)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
    }

    val cardShape = remember { Shapes.cardCompact }

    val episodeLabel = remember(movie.season, movie.episode) {
        if (movie.season != null && movie.episode != null) {
            "S${movie.season} E${movie.episode}"
        } else null
    }

    var progressTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(movie.watchProgress) {
        progressTarget = if (movie.watchProgress != null && movie.watchProgress > 0) movie.watchProgress.toFloat() / 100f else 0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "continueProgress"
    )



    Column(
        modifier = modifier.width(CardDefaults.compactWidth)
    ) {
        Surface(
            onClick = onClick,
            onLongClick = onDismiss,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(CardDefaults.compactHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            val sharedContentState = rememberSharedContentState(key = "poster_${movie.id}")
                            Modifier.sharedElement(sharedContentState = sharedContentState, animatedVisibilityScope = animatedVisibilityScope)
                        }
                    } else Modifier
                )
                .onKeyEvent { event ->
                    if (onDismiss != null) {
                        val isMenu = event.key == Key.Menu || event.key == Key.Settings
                        val isDelete = event.key == Key.MediaSkipBackward
                        if ((isMenu || isDelete) && event.type == KeyEventType.KeyUp) {
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
                                .fillMaxWidth(animatedProgress)
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
