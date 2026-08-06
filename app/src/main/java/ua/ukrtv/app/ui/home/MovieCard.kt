package ua.ukrtv.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.PosterStyle
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalFormFactor
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.PlaceholderDark
import ua.ukrtv.app.ui.theme.deviceImage
import ua.ukrtv.app.util.DeviceClass

private val cardShape = RoundedCornerShape(8.dp)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MovieCard(
    movie: Movie,
    brandColor: Color = Color(0xFF6E85B7),
    accentColor: Color = brandColor,
    width: Dp = CardDefaults.posterWidth,
    height: Dp = CardDefaults.posterHeight,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val actualDismiss = onLongClick ?: onDismiss
    val ctx = LocalContext.current
    val formFactor = LocalFormFactor.current
    val isTv = formFactor == FormFactor.TV
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val deviceClass = LocalDeviceClass.current
    val isMediatek = LocalIsMediatek.current
    val density = LocalDensity.current.density

    val targetScale = when (deviceClass) {
        DeviceClass.LOW -> 1.05f
        DeviceClass.MID -> 1.08f
        DeviceClass.HIGH -> 1.1f
    }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardScale"
    )

    val translateY by animateFloatAsState(
        targetValue = if (isFocused && deviceClass == DeviceClass.HIGH) (-6f) else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardTranslateY"
    )

    val playIconAlpha by animateFloatAsState(
        targetValue = if (isFocused && isTv) 1f else 0f,
        animationSpec = tween(200),
        label = "playIconAlpha"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isFocused || !isTv) 1f else 0f,
        animationSpec = tween(300),
        label = "contentAlpha"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused && isTv) 0.5f else 0f,
        animationSpec = tween(400),
        label = "glowAlpha"
    )

    val posterStyle = remember(movie.provider) {
        PosterStyle.forProvider(movie.provider)
    }

    val imageRequest = remember(movie.poster, deviceClass, posterStyle, width, height) {
        if (movie.poster.isNullOrBlank()) return@remember null

        val styleForImage = if (width > height) PosterStyle.WIDE else PosterStyle.VERTICAL

        val (iw, ih) = when (styleForImage) {
            PosterStyle.WIDE -> when (deviceClass) {
                DeviceClass.LOW -> 320 to 180
                DeviceClass.MID -> 480 to 270
                DeviceClass.HIGH -> 640 to 360
            }
            PosterStyle.VERTICAL -> when (deviceClass) {
                DeviceClass.LOW -> 160 to 240
                DeviceClass.MID -> 320 to 480
                DeviceClass.HIGH -> 480 to 720
            }
        }
        ImageRequest.Builder(ctx)
            .data(movie.poster)
            .size(iw, ih)
            .deviceImage(deviceClass, isMediatek)
            .crossfade(if (deviceClass == DeviceClass.HIGH) 100 else 0)
            .build()
    }

    val sharedModifier = if (sharedTransitionScope != null && animatedContentScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "movie_poster_${movie.id}"),
                animatedVisibilityScope = animatedContentScope
            )
        }
    } else Modifier

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .testTag("movie_item")
            .then(sharedModifier)
            .drawBehind {
                if (glowAlpha > 0f && deviceClass != DeviceClass.LOW) {
                    val gColor = accentColor.copy(alpha = glowAlpha * 0.4f)
                    val glowPadding = 12.dp.toPx()
                    val s = this.size
                    drawRoundRect(
                        color = gColor,
                        topLeft = Offset(-glowPadding, -glowPadding),
                        size = androidx.compose.ui.geometry.Size(s.width + glowPadding * 2, s.height + glowPadding * 2),
                        cornerRadius = CornerRadius(16.dp.toPx())
                    )
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = translateY * density
                
                if (isFocused) {
                    shadowElevation = if (deviceClass == DeviceClass.HIGH) 16.dp.toPx() else 8.dp.toPx()
                    spotShadowColor = accentColor.copy(alpha = if (deviceClass == DeviceClass.HIGH) 0.4f else 0.2f)
                    ambientShadowColor = accentColor.copy(alpha = if (deviceClass == DeviceClass.HIGH) 0.6f else 0.3f)
                }
                
                clip = true
                shape = cardShape
            }
            .background(accentColor.copy(alpha = 0.15f))
            .clickable(
                interactionSource = interactionSource,
                indication = if (formFactor == FormFactor.PHONE) ripple() else null,
                onClick = onClick
            )
            .onKeyEvent { event ->
                if (actualDismiss != null) {
                    val isMenu = event.key == Key.Menu || event.key == Key.Settings
                    if (isMenu && event.type == KeyEventType.KeyUp) {
                        actualDismiss()
                        return@onKeyEvent true
                    }
                }
                false
            }
            .then(
                if (isFocused) {
                    val borderColor = when {
                        deviceClass == DeviceClass.HIGH -> Color.White
                        deviceClass == DeviceClass.MID -> accentColor
                        else -> Color.White.copy(alpha = 0.8f)
                    }
                    val borderWidth = when (deviceClass) {
                        DeviceClass.HIGH -> 3.dp
                        DeviceClass.MID -> 2.dp
                        DeviceClass.LOW -> 2.dp
                    }
                    Modifier.border(BorderStroke(borderWidth, borderColor), cardShape)
                } else Modifier
            )
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            placeholder = PlaceholderDark,
            error = PlaceholderDark
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = playIconAlpha
                    val s = 0.8f + (playIconAlpha * 0.2f)
                    scaleX = s
                    scaleY = s
                }
                .size(48.dp)
                .background(brandColor.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        if (movie.provider != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(brandColor.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = movie.provider.uppercase(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, end = 10.dp, bottom = 6.dp)
                .graphicsLayer { alpha = contentAlpha }
        ) {
            Text(
                text = movie.title.uppercase(),
                color = Color.White,
                fontSize = 12.sp,
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
                            text = "\u2605",
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
                if (!movie.duration.isNullOrEmpty()) {
                    Text(
                        text = movie.duration,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
