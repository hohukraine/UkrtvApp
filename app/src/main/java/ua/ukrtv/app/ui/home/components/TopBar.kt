package ua.ukrtv.app.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import ua.ukrtv.app.domain.model.Provider
import ua.ukrtv.app.ui.theme.GridDefaults

/**
 * TopBar - Professional Navigation & Branding
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TopBar(
    brandColor: Color,
    providers: List<Provider>,
    currentProviderId: String,
    onSearchClick: () -> Unit,
    onProviderClick: (String) -> Unit,
    searchFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GridDefaults.horizontalPadding)
            .padding(top = 16.dp, bottom = 8.dp)
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            contentColor = if (isSelected) brandColor else Color.White.copy(alpha = 0.5f),
            focusedContentColor = brandColor
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        modifier = modifier.height(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }
    }
}
