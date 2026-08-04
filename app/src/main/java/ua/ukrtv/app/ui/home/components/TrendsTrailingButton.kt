package ua.ukrtv.app.ui.home.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.PosterStyle
import ua.ukrtv.app.ui.theme.ProviderSizes
import ua.ukrtv.app.util.DeviceClass

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TrendsTrailingButton(
    brandColor: Color,
    onClick: () -> Unit,
    useLargeCards: Boolean = false,
    provider: String? = null
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

    val posterStyle = remember(provider) { PosterStyle.forProvider(provider) }
    val dims = ProviderSizes.card(posterStyle)

    val baseHeight = if (useLargeCards) dims.height * 1.15f else dims.height
    val baseWidth = dims.width * 0.6f

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = brandColor.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .width((baseWidth * cardScale))
            .height((baseHeight * cardScale))
            .focusProperties {
                exit = { focusDirection ->
                    if (focusDirection == FocusDirection.Right) FocusRequester.Cancel
                    else FocusRequester.Default
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp * cardScale)
            )
            if (isFocused) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Більше",
                    color = Color.White,
                    fontSize = (14.sp.value * cardScale).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
