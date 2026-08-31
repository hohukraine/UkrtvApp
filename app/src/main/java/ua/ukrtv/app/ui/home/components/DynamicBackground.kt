package ua.ukrtv.app.ui.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.util.DeviceClass

@Composable
fun HomeBackground(
    focusedColor: Color,
    brandColor: Color,
    backdropColor: Color = Color.Unspecified,
    backdropUrl: String? = null,
    backdropBlur: Dp = 0.dp,
    scrollFraction: () -> Float = { 0f },
    // Netflix/Apple TV banner mode: full-bleed image, no blur, no color cast —
    // only soft black scrims for text readability.
    immersive: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val deviceClass = LocalDeviceClass.current
    val isMediatek = LocalIsMediatek.current
    val animateGlow = deviceClass != DeviceClass.LOW && !isMediatek

    // TMDB uses a specific primary color for the movie
    val primaryColor = remember(backdropColor) {
        if (backdropColor != Color.Unspecified) backdropColor else Color(0xFF032541)
    }

    val animatedPrimaryColor by animateColorAsState(
        targetValue = primaryColor,
        animationSpec = tween(1000),
        label = "bgPrimaryColor"
    )

    val animatedFocusColor by animateColorAsState(
        targetValue = focusedColor,
        animationSpec = tween(800),
        label = "bgFocusAccent"
    )

    Box(modifier = modifier.fillMaxSize().background(Background)) {
        // LAYER 1: Backdrop Image (Full screen) — parallax: moves slower than scroll
        if (!backdropUrl.isNullOrEmpty() && deviceClass != DeviceClass.LOW) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(backdropUrl)
                    .crossfade(1000)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (immersive) 1f else 0.9f)
                    .then(
                        if (!immersive && backdropBlur > 0.dp && deviceClass == DeviceClass.HIGH && !isMediatek) {
                            Modifier.blur(backdropBlur)
                        } else Modifier
                    )
                    .graphicsLayer {
                        val scroll = scrollFraction()
                        // Parallax: backdrop scrolls at 40% of content speed
                        translationY = scroll * size.height * 0.15f
                        alpha = (1f - scroll * 0.7f).coerceIn(0.15f, 1f)
                    }
            )
        }

        // LAYER 2: TMDB/Netflix Signature Overlay — multi-layer parallax gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val scroll = scrollFraction()
                    val s = size
                    val color = animatedPrimaryColor

                    if (immersive) {
                        val hasImage = !backdropUrl.isNullOrEmpty() && deviceClass != DeviceClass.LOW
                        if (!hasImage) {
                            // Solid color gradient fill for low-end immersive
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        color.copy(alpha = 0.9f),
                                        color.copy(alpha = 0.55f),
                                        color.copy(alpha = 0.25f),
                                        Background
                                    )
                                )
                            )
                            return@drawBehind
                        }
                        // Netflix-style cinematic scrims with parallax
                        // Layer A: Left vignette (moves at 30% speed)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Transparent
                                ),
                                startX = 0f,
                                endX = s.width * 0.75f
                            ),
                            alpha = (1f - scroll * 0.35f).coerceIn(0f, 1f)
                        )
                        // Layer B: Top bar darkening (moves at 20% speed)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = s.height * 0.35f
                            ),
                            alpha = (1f - scroll * 0.2f).coerceIn(0.3f, 1f)
                        )
                        // Layer C: Bottom fade (scrolls with content)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Background.copy(alpha = 0.5f),
                                    Background
                                ),
                                startY = s.height * (0.6f - scroll * 0.05f),
                                endY = s.height
                            )
                        )
                        // Layer D: Ambient glow (drifts with scroll)
                        if (animateGlow) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        animatedFocusColor.copy(alpha = 0.08f),
                                        Color.Transparent
                                    ),
                                    center = Offset(
                                        s.width * 0.15f,
                                        s.height * (0.45f - scroll * 0.08f)
                                    ),
                                    radius = s.width * 1.8f
                                )
                            )
                        }
                        return@drawBehind
                    }

                    // Non-immersive: dim blurred poster backdrop
                    if (backdropBlur > 0.dp) {
                        drawRect(color = Color.Black.copy(alpha = 0.35f))
                    }

                    // Layer A: TMDB color wash — horizontal gradient tinting the poster
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 1.00f),
                                color.copy(alpha = 0.92f),
                                color.copy(alpha = 0.60f)
                            ),
                            startX = 0f,
                            endX = s.width
                        ),
                        alpha = (1f - scroll * 0.4f).coerceIn(0f, 1f)
                    )

                    // Layer B: Vertical blend — fade to background at bottom
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Transparent,
                                Background.copy(alpha = 0.85f),
                                Background
                            ),
                            startY = 0f,
                            endY = s.height
                        )
                    )

                    // Layer C: Ambient glow — radial light from focused card
                    if (animateGlow) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    animatedFocusColor.copy(alpha = 0.12f),
                                    Color.Transparent
                                ),
                                center = Offset(s.width * 0.1f, s.height * 0.4f),
                                radius = s.width * 1.5f
                            )
                        )
                    }

                    // Layer D: Subtle vignette — darken edges for depth
                    if (animateGlow) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.15f)
                                ),
                                center = Offset(s.width * 0.5f, s.height * 0.5f),
                                radius = s.width * 0.85f
                            )
                        )
                    }
                }
        )

        content()
    }
}

@Composable
fun Modifier.providerBackground(providerColor: Color): Modifier {
    val deviceClass = LocalDeviceClass.current
    val alpha = when (deviceClass) {
        DeviceClass.LOW -> 0.02f
        else -> 0.04f
    }
    return this.drawWithCache {
        val centerX = size.width * 0.25f
        val centerY = size.height * 0.15f
        val radius = size.width.coerceAtLeast(size.height) * 1.2f
        val brush = Brush.radialGradient(
            colors = listOf(
                providerColor.copy(alpha = alpha),
                providerColor.copy(alpha = alpha * 0.5f),
                Color.Transparent
            ),
            center = Offset(centerX, centerY),
            radius = radius
        )
        onDrawBehind {
            drawRect(brush = brush)
        }
    }
}
