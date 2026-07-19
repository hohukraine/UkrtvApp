package ua.ukrtv.app.ui.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun BottomNavBar(
    currentRoute: String,
    brandColor: Color,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMyListClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF141414),
        contentColor = Color.White
    ) {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = onHomeClick,
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Головна", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = brandColor,
                selectedTextColor = brandColor,
                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                unselectedTextColor = Color.White.copy(alpha = 0.5f),
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = currentRoute == "search",
            onClick = onSearchClick,
            icon = { Icon(Icons.Default.Search, contentDescription = "Пошук") },
            label = { Text("Пошук", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = brandColor,
                selectedTextColor = brandColor,
                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                unselectedTextColor = Color.White.copy(alpha = 0.5f),
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = currentRoute == "mylist",
            onClick = onMyListClick,
            icon = { Icon(Icons.Default.Favorite, contentDescription = "My List") },
            label = { Text("Мій список", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = brandColor,
                selectedTextColor = brandColor,
                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                unselectedTextColor = Color.White.copy(alpha = 0.5f),
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = currentRoute == "settings",
            onClick = onSettingsClick,
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Налаштування", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = brandColor,
                selectedTextColor = brandColor,
                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                unselectedTextColor = Color.White.copy(alpha = 0.5f),
                indicatorColor = Color.Transparent
            )
        )
    }
}
