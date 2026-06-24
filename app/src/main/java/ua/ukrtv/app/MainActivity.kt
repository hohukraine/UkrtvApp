package ua.ukrtv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import dagger.hilt.android.AndroidEntryPoint
import ua.ukrtv.app.navigation.AppNavigation
import ua.ukrtv.app.ui.detail.DetailScreen
import ua.ukrtv.app.ui.home.HomeScreen
import ua.ukrtv.app.ui.search.SearchScreen
import ua.ukrtv.app.ui.player.PlayerScreen
import ua.ukrtv.app.ui.theme.UkrtvTheme
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.keepScreenOn = true
        super.onCreate(savedInstanceState)
        setContent {
            UkrtvTheme {
                UkrtvTVApp()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU) {
            // Global "Menu" button handling (Point 6)
            ua.ukrtv.app.util.AppLogger.d("MainActivity", "Menu button pressed - Quick Settings trigger")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
@Composable
fun UkrtvTVApp() {
    val navController = rememberNavController()

    val onMovieClick = remember(navController) {
        { movie: ua.ukrtv.app.domain.model.Movie ->
            navController.navigate(AppNavigation.detailRoute(movie.id, movie.pageUrl, movie.type.name))
        }
    }
    val onSearchClick = remember(navController) {
        { navController.navigate(AppNavigation.SEARCH) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = Color(0xFF0C0C0D)) // backgroundMinimal
    ) {
        NavHost(
            navController = navController,
            startDestination = AppNavigation.HOME,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            composable(AppNavigation.HOME) {
                HomeScreen(
                    onMovieClick = onMovieClick,
                    onSearchClick = onSearchClick
                )
            }
            composable(AppNavigation.SEARCH) {
                SearchScreen(
                    onMovieClick = { movie ->
                        navController.navigate(AppNavigation.detailRoute(movie.id, movie.pageUrl, movie.type.name))
                    }
                )
            }
            composable(
                route = AppNavigation.DETAIL,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType },
                    navArgument("type") { type = NavType.StringType; nullable = true }
                )
            ) {
                DetailScreen(
                    onPlayClick = { launchState ->
                        if (launchState is ua.ukrtv.app.domain.model.MediaLaunchState.Ready) {
                            navController.navigate(
                                AppNavigation.playerRoute(
                                    launchState.contentId,
                                    launchState.title,
                                    url = launchState.streamResult.sourcePageUrl,
                                    poster = launchState.posterUrl,
                                    season = launchState.season,
                                    episode = launchState.episode
                                )
                            )
                            // Pass the full result for instant playback
                            navController.currentBackStackEntry?.savedStateHandle?.set("launch_state", launchState)
                        }
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
                    navArgument("episode") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("poster") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: ""
                val url = backStackEntry.arguments?.getString("url") ?: ""
                val poster = backStackEntry.arguments?.getString("poster") ?: ""
                val season = backStackEntry.arguments?.getInt("season")?.takeIf { it != -1 }
                val episode = backStackEntry.arguments?.getInt("episode")?.takeIf { it != -1 }

                PlayerScreen(
                    url = url,
                    contentId = id,
                    title = title,
                    poster = poster,
                    season = season,
                    episode = episode,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
