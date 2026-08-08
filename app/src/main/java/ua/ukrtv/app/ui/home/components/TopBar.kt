package ua.ukrtv.app.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import ua.ukrtv.app.ui.theme.GridDefaults
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.TopBarDefaults
import ua.ukrtv.app.util.DeviceClass

/**
 * TopBar - shared header for TV and phone.
 *
 * The floating glass panel uses one composition on every preset; only the gradient
 * intensity scales: LOW = faint static glass, MID = static glass, HIGH = scroll-reactive.
 */
@Composable
fun TopBar(
    brandColor: Color,
    currentProviderId: String,
    scrollFraction: () -> Float = { 0f },
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    val deviceClass = LocalDeviceClass.current
    val scrollDensity = remember { derivedStateOf { scrollFraction() } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GridDefaults.horizontalPadding - 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(TopBarDefaults.panelRadius))
            .drawWithCache {
                onDrawBehind {
                    val f = scrollDensity.value
                    val topAlpha: Float
                    when (deviceClass) {
                        DeviceClass.LOW -> topAlpha = 0.05f
                        DeviceClass.MID -> topAlpha = 0.10f
                        DeviceClass.HIGH -> topAlpha = (0.08f + 0.25f * f).coerceIn(0f, 0.33f)
                    }
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = topAlpha),
                                Color.White.copy(alpha = topAlpha * 0.4f)
                            )
                        )
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = TopBarDefaults.actionHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoLockup(brandColor = brandColor)

            Spacer(Modifier.width(14.dp))

            ProviderChip(providerName = currentProviderId, brandColor = brandColor)

            Spacer(Modifier.weight(1f))

            SearchAction(
                brandColor = brandColor,
                onClick = onSearchClick,
                focusRequester = searchFocusRequester
            )

            Spacer(Modifier.width(12.dp))

            SettingsAction(brandColor = brandColor, onClick = onSettingsClick)
        }
    }
}

@Composable
fun LogoLockup(
    brandColor: Color,
    fontSize: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "UKR",
            color = Color.White,
            fontSize = fontSize.value.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Text(
            text = "TV",
            color = brandColor,
            fontSize = fontSize.value.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun ProviderChip(
    providerName: String,
    brandColor: Color,
    fontSize: Dp = 13.dp,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val label = providerName.ifBlank { "UAFLIX" }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(brandColor.copy(alpha = 0.14f))
            .border(1.dp, brandColor.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 3.dp else 5.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = brandColor,
            fontSize = fontSize.value.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = if (compact) 0.5.sp else 1.2.sp
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchAction(
    brandColor: Color,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val height = if (compact) 34.dp else TopBarDefaults.actionHeight
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (compact) 1.05f else 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = brandColor.copy(alpha = 0.14f),
            contentColor = Color.White.copy(alpha = 0.55f),
            focusedContentColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        modifier = modifier
            .height(height)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = if (compact) 14.dp else 18.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Пошук",
                modifier = Modifier.size(if (compact) 16.dp else 18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Пошук",
                fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsAction(
    brandColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = brandColor.copy(alpha = 0.14f),
            contentColor = Color.White.copy(alpha = 0.55f),
            focusedContentColor = brandColor
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        modifier = modifier.size(TopBarDefaults.actionHeight)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Налаштування",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
