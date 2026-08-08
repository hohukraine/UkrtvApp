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
        // LAYER 1: Backdrop Image (Full screen)
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
                    .fillMaxHeight(0.9f)
                    .then(
                        if (backdropBlur > 0.dp && deviceClass == DeviceClass.HIGH && !isMediatek) {
                            Modifier.blur(backdropBlur)
                        } else Modifier
                    )
                    .graphicsLayer {
                        val scroll = scrollFraction()
                        // Cinematic visibility: High but tinted by the overlay
                        alpha = (1f - scroll * 0.8f).coerceIn(0.2f, 1f)
                    }
            )
        }

        // LAYER 2: TMDB/Netflix Signature Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val scroll = scrollFraction()
                    val s = size
                    val color = animatedPrimaryColor

                    // Dim the blurred poster backdrop so rows stay readable
                    if (backdropBlur > 0.dp) {
                        drawRect(color = Color.Black.copy(alpha = 0.35f))
                    }

                    // 2.1 THE TMDB "WASH" - A heavy gradient of the primary color over the image
                    // This creates the exact TMDB look where the image is tinted by the movie color.
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 1.00f), // Left: 100% Solid
                                color.copy(alpha = 0.92f), // Mid-left: 92%
                                color.copy(alpha = 0.60f)  // Right: 60% (Shows image through)
                            ),
                            startX = 0f,
                            endX = s.width
                        ),
                        alpha = (1f - scroll * 0.4f).coerceIn(0f, 1f)
                    )

                    // 2.2 VERTICAL BLEND - Fade to pure app background color at the bottom
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f), // Top shadow
                                Color.Transparent,
                                Color.Transparent,
                                Background.copy(alpha = 0.85f), // Start transition to black
                                Background                      // Pure black
                            ),
                            startY = 0f,
                            endY = s.height
                        )
                    )

                    // 2.3 AMBIENT GLOW
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
