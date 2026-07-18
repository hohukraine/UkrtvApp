package ua.ukrtv.app.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import ua.ukrtv.app.domain.model.Provider
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.GridDefaults
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.util.DeviceClass

/**
 * TopBar - Professional Navigation & Branding
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TopBar(
    brandColor: Color,
    providers: List<Provider>,
    currentProviderId: String,
    scrollFraction: () -> Float = { 0f },
    onSearchClick: () -> Unit,
    onProviderClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    searchFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val deviceClass = LocalDeviceClass.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (deviceClass == DeviceClass.HIGH) {
                    Modifier
                        .padding(horizontal = GridDefaults.horizontalPadding - 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                } else {
                    Modifier
                        .padding(horizontal = GridDefaults.horizontalPadding)
                        .padding(top = 16.dp, bottom = 8.dp)
                }
            )
            .drawBehind {
                val fraction = scrollFraction()
                if (deviceClass == DeviceClass.HIGH) {
                    val bgBrightness = (0.08f + 0.25f * fraction).coerceIn(0f, 0.33f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = bgBrightness),
                                Color.White.copy(alpha = bgBrightness * 0.4f)
                            )
                        )
                    )
                } else {
                    val bgAlpha = when (deviceClass) {
                        DeviceClass.LOW -> (fraction * 0.3f).coerceIn(0f, 0.3f)
                        DeviceClass.MID -> (fraction * 0.5f).coerceIn(0f, 0.5f)
                        DeviceClass.HIGH -> 0f
                    }
                    if (bgAlpha > 0.01f) {
                        drawRect(color = Background.copy(alpha = bgAlpha))
                    }
                }
            }
            .then(
                if (deviceClass == DeviceClass.HIGH) {
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                } else {
                    Modifier
                }
            )
    ) {
        // App Identity (Left)
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UKR",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Text(
                text = "TV",
                color = brandColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }

        // Navigation (Center)
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NavButton(
                text = "ПОШУК",
                isSelected = false,
                brandColor = brandColor,
                onClick = onSearchClick,
                modifier = Modifier.focusRequester(searchFocusRequester)
            )

            providers.forEach { provider ->
                NavButton(
                    text = provider.name.uppercase(),
                    isSelected = provider.name == currentProviderId,
                    brandColor = brandColor,
                    onClick = { onProviderClick(provider.name) }
                )
            }
        }

        // Settings icon (Right)
        SettingsButton(
            brandColor = brandColor,
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsButton(
    brandColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            contentColor = Color.White.copy(alpha = 0.5f),
            focusedContentColor = brandColor
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        modifier = modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Налаштування",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavButton(
    text: String,
    isSelected: Boolean,
    brandColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = brandColor.copy(alpha = 0.1f),
            contentColor = if (isSelected) brandColor else Color.White.copy(alpha = 0.5f),
            focusedContentColor = brandColor
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        modifier = modifier.height(38.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .width(16.dp)
                            .height(3.dp)
                            .background(brandColor, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}
