package ua.ukrtv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import ua.ukrtv.app.ui.player.SeasonSelectorScreen
import ua.ukrtv.app.ui.player.SeasonSelectionResult
import ua.ukrtv.app.ui.top200.Top200Screen
import ua.ukrtv.app.ui.theme.UkrtvTheme
import ua.ukrtv.app.domain.model.Top200Movie

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        window.decorView.keepScreenOn = true
        window.setBackgroundDrawable(null)
        super.onCreate(savedInstanceState)
        setContent {
            UkrtvTheme {
                UkrtvTVApp()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU) {
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
            ua.ukrtv.app.util.Perf.start("nav:home2detail")
            ua.ukrtv.app.util.AppLogger.d("Navigation", "home→detail: movie=${movie.title} url=${movie.pageUrl?.take(40)}")
            navController.navigate(AppNavigation.detailRoute(movie.id, movie.pageUrl))
        }
    }
    val onSearchClick = remember(navController) {
        { navController.navigate(AppNavigation.searchRoute()) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = Color.Transparent)
    ) {
        NavHost(
            navController = navController,
            startDestination = AppNavigation.HOME,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            composable(
                route = AppNavigation.SEASONS,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("season") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("episode") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("voiceover") { type = NavType.StringType; defaultValue = "" }
                )
            ) {
                SeasonSelectorScreen(
                    onBack = { navController.popBackStack() },
                    onEpisodeSelected = { result ->
                        val prevEntry = navController.previousBackStackEntry
                        prevEntry?.savedStateHandle?.set("episode_result", result)
                        navController.popBackStack()
                    }
                )
            }

            composable(AppNavigation.HOME) {
                HomeScreen(
                    onMovieClick = onMovieClick,
                    onSearchClick = onSearchClick,
                    onTop200Click = { navController.navigate(AppNavigation.TOP_200) },
                    onTop200ItemClick = { movie ->
                        navController.navigate(AppNavigation.searchRoute(movie.title))
                    }
                )
            }
            composable(AppNavigation.TOP_200) {
                Top200Screen(
                    onMovieClick = { movie ->
                        navController.navigate(AppNavigation.searchRoute(movie.title))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = AppNavigation.SEARCH,
                arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })
            ) {
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
                    onBack = { navController.popBackStack() },
                    onNavigateToSeasons = {
                        navController.navigate(
                            AppNavigation.seasonsRoute(
                                id = id,
                                url = url,
                                title = title,
                                season = season,
                                episode = episode
                            )
                        )
                    }
                )
            }
        }
    }
}
