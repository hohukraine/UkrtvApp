package ua.ukrtv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import dagger.hilt.android.AndroidEntryPoint
import ua.ukrtv.app.navigation.AppNavigation
import ua.ukrtv.app.ui.detail.DetailScreen
import ua.ukrtv.app.ui.home.HomeScreen
import ua.ukrtv.app.ui.search.SearchScreen
import ua.ukrtv.app.ui.player.PlayerScreen
import ua.ukrtv.app.ui.player.PlayerActivity
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.ui.theme.UkrtvTheme
import kotlinx.coroutines.delay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.keepScreenOn = true
        super.onCreate(savedInstanceState)
        setContent {
            UkrtvTheme {
                var showContent by remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    delay(300)
                    showContent = true
                }
                
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!showContent) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BrandBlue)
                        }
                    } else {
                        UkrtvTVApp()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UkrtvTVApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = Color(0xFF0C0C0D)) // backgroundMinimal
    ) {
        NavHost(
            navController = navController, 
            startDestination = AppNavigation.HOME,
            enterTransition = { fadeIn(animationSpec = tween(400)) },
            exitTransition = { fadeOut(animationSpec = tween(400)) }
        ) {
            composable(AppNavigation.HOME) {
                HomeScreen(
                    onMovieClick = { movie ->
                        navController.navigate(AppNavigation.detailRoute(movie.id, movie.pageUrl))
                    },
                    onSearchClick = {
                        navController.navigate(AppNavigation.SEARCH)
                    }
                )
            }
            composable(AppNavigation.SEARCH) {
                SearchScreen(
                    onMovieClick = { movie ->
                        navController.navigate(AppNavigation.detailRoute(movie.id, movie.pageUrl))
                    }
                )
            }
            composable(
                route = AppNavigation.DETAIL,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val url = backStackEntry.arguments?.getString("url") ?: ""
                DetailScreen(
                    onPlayClick = { playbackResult, title, season, episode, id, seasons ->
                        navController.navigate(
                            AppNavigation.playerRoute(id, title ?: "", url = url, season = season, episode = episode)
                        )
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = AppNavigation.PLAYER,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType },
                    navArgument("season") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("episode") { type = NavType.IntType; defaultValue = -1 }
                )
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: ""
                val url = backStackEntry.arguments?.getString("url") ?: ""
                val season = backStackEntry.arguments?.getInt("season")?.takeIf { it != -1 }
                val episode = backStackEntry.arguments?.getInt("episode")?.takeIf { it != -1 }

                PlayerScreen(
                    uakinoUrl = url,
                    contentId = id,
                    title = title,
                    season = season,
                    episode = episode,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
