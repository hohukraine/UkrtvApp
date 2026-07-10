package ua.ukrtv.app.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.util.DeviceClass

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TrendsTrailingButton(
    brandColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val deviceClass = LocalDeviceClass.current
    val cardScale = remember(deviceClass) {
        when (deviceClass) {
            DeviceClass.LOW -> 0.75f
            DeviceClass.MID -> 1.0f
            DeviceClass.HIGH -> 1.25f
        }
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = brandColor.copy(alpha = 0.15f)
        ),
        modifier = Modifier
            .width((48.dp * cardScale))
            .height((276.dp * cardScale))
            .graphicsLayer {
                scaleX = if (isFocused) 1.05f else 1f
                scaleY = if (isFocused) 1.05f else 1f
            }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Всі тренди",
                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.width(24.dp * cardScale)
            )
        }
    }
}
