package ua.ukrtv.app.ui.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.util.DeviceClass

@Composable
fun HomeBackground(
    focusedColor: Color,
    brandColor: Color,
    backdropColor: Color = Color.Unspecified,
    scrollFraction: () -> Float = { 0f },
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val deviceClass = LocalDeviceClass.current
    val isMediatek = LocalIsMediatek.current
    val animateGlow = deviceClass != DeviceClass.LOW && !isMediatek

    val providerAlpha = when {
        deviceClass == DeviceClass.LOW -> 0.04f
        deviceClass == DeviceClass.MID -> 0.08f
        else -> 0.12f
    }
    val focusAlpha = when (deviceClass) {
        DeviceClass.LOW -> 0.08f
        DeviceClass.MID -> 0.14f
        DeviceClass.HIGH -> 0.22f
    }
    val animDuration = when {
        !animateGlow -> 0
        deviceClass == DeviceClass.MID -> 800
        else -> 1200
    }

    // Feature 6: Ambient Gradient Motion — subtle oscillation of glow centers
    // Uses InfiniteTransition instead of frame-by-frame floatState writes to avoid
    // recomposing the parent Box on every frame (~60fps).
    val motionRange = when (deviceClass) {
        DeviceClass.MID -> 0.015f
        DeviceClass.HIGH -> 0.03f
        else -> 0f
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bgMotion")
    val motionXState = infiniteTransition.animateFloat(
        initialValue = -motionRange,
        targetValue = motionRange,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "motionX"
    )
    val motionYState = infiniteTransition.animateFloat(
        initialValue = -motionRange * 0.6f,
        targetValue = motionRange * 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "motionY"
    )
    val motionPhase2XState = infiniteTransition.animateFloat(
        initialValue = -motionRange * 0.5f,
        targetValue = motionRange * 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "motionPhase2X"
    )

    val animatedFocusColor by animateColorAsState(
        targetValue = focusedColor,
        animationSpec = tween(animDuration),
        label = "bgFocusAccent"
    )
    val animatedBrandColor by animateColorAsState(
        targetValue = brandColor,
        animationSpec = tween(animDuration),
        label = "bgBrandAccent"
    )

    // Feature 2: Backdrop wash — vertical gradient from banner accent
    val washDuration = when (deviceClass) {
        DeviceClass.LOW -> 0
        DeviceClass.MID -> 400
        DeviceClass.HIGH -> 600
    }
    
    // We keep this in composition as it affects whether the layer exists
    val washTargetAlpha = if (backdropColor == Color.Unspecified) 0f else 1f
    
    val animatedWashAlphaState = animateFloatAsState(
        targetValue = washTargetAlpha,
        animationSpec = tween(washDuration),
        label = "backdropWashAlpha"
    )
    val washLayerAlpha = when (deviceClass) {
        DeviceClass.LOW -> 0.12f
        DeviceClass.MID -> 0.20f
        DeviceClass.HIGH -> 0.28f
    }
    val washHeightFraction = when (deviceClass) {
        DeviceClass.LOW -> 0.35f
        DeviceClass.MID -> 0.45f
        DeviceClass.HIGH -> 0.55f
    }

    Box(modifier = modifier.fillMaxSize().background(Background)) {
        if (animateGlow) {
            // 2.2 Deferred reads using drawBehind
            Box(modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val scroll = scrollFraction().coerceIn(0f, 1f)
                    val motionX = motionXState.value
                    val motionY = motionYState.value
                    val motionPhase2X = motionPhase2XState.value
                    val animatedWashAlpha = animatedWashAlphaState.value
                    
                    // Layer 0: Backdrop color wash
                    val currentWashAlpha = animatedWashAlpha * (1f - scroll * 1.1f).coerceIn(0f, 1f)
                    if (currentWashAlpha > 0.001f && backdropColor != Color.Unspecified) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    backdropColor.copy(alpha = washLayerAlpha * currentWashAlpha),
                                    backdropColor.copy(alpha = washLayerAlpha * 0.3f * currentWashAlpha),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * washHeightFraction
                            )
                        )
                    }

                    // Feature 3: Provider boost
                    val providerBoost = 1f + 0.5f * scroll
                    val effectiveProviderAlpha = providerAlpha * providerBoost

                    // Layer 1: Provider ambient glow
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedBrandColor.copy(alpha = effectiveProviderAlpha),
                                animatedBrandColor.copy(alpha = effectiveProviderAlpha * 0.5f),
                                Color.Transparent
                            ),
                            center = Offset(
                                size.width * (0.25f + motionX),
                                size.height * (0.15f + motionY)
                            ),
                            radius = size.width * 1.4f
                        )
                    )

                    // Layer 2: Content accent glow
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedFocusColor.copy(alpha = focusAlpha),
                                animatedFocusColor.copy(alpha = focusAlpha * 0.3f),
                                Color.Transparent
                            ),
                            center = Offset(
                                size.width * (0.5f + motionX * 0.7f),
                                size.height * (0.3f + motionY * 0.7f)
                            ),
                            radius = size.width * 1.6f
                        )
                    )

                    // Layer 3: Secondary brand glow (only HIGH)
                    if (deviceClass == DeviceClass.HIGH) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    animatedBrandColor.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                center = Offset(
                                    size.width * (0.9f + motionPhase2X),
                                    size.height * (0.9f + motionY * 0.5f)
                                ),
                                radius = size.width * 0.7f
                            )
                        )
                    }
                }
            )
        }
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
