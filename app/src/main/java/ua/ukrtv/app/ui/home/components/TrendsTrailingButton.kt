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
import ua.ukrtv.app.ui.theme.YouTv
import ua.ukrtv.app.util.DeviceClass

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TrendsTrailingButton(
    brandColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useLargeCards: Boolean = false,
    provider: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Match the exact card dimensions from MainSectionRow (YouTv layout)
    val cardW = if (useLargeCards) YouTv.collectionCardW else YouTv.vodCardW
    val cardH = if (useLargeCards) YouTv.collectionCardH else YouTv.vodCardH

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(YouTv.cardRadius)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = brandColor.copy(alpha = 0.25f)
        ),
        modifier = modifier
            .width(cardW)
            .height(cardH)
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
                modifier = Modifier.size(32.dp)
            )
            if (isFocused) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Більше",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
