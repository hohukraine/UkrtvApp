package ua.ukrtv.app.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import ua.ukrtv.app.domain.model.Provider

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
    val searchSurfaceScale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
    val searchSurfaceColors = ClickableSurfaceDefaults.colors(
        containerColor = Color(0xFF1E3A8A),
        focusedContainerColor = Color.White,
        contentColor = Color.White,
        focusedContentColor = Color.Black
    )
    val searchSurfaceShape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50))

    val providerSurfaceScale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
    val providerSurfaceShape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp))
    val providerUnselectedColor = remember { Color.White.copy(alpha = 0.1f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 58.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Search Button
            Surface(
                onClick = onSearchClick,
                scale = searchSurfaceScale,
                colors = searchSurfaceColors,
                shape = searchSurfaceShape,
                modifier = Modifier
                    .size(48.dp)
                    .focusRequester(searchFocusRequester)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(32.dp))

            // Provider Switcher
            providers.forEach { provider ->
                val isSelected = provider.id == currentProviderId
                
                Surface(
                    onClick = { onProviderClick(provider.id) },
                    scale = providerSurfaceScale,
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) brandColor else providerUnselectedColor,
                        focusedContainerColor = Color.White,
                        contentColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    shape = providerSurfaceShape,
                    modifier = Modifier
                        .height(32.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = provider.name.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // App Logo
        Text(
            text = "UKR TV",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )
    }
}
