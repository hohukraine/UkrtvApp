package ua.ukrtv.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ua.ukrtv.app.ui.theme.detectFormFactor
import ua.ukrtv.app.ui.theme.FormFactor
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
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import ua.ukrtv.app.ui.detail.DetailScreen
import ua.ukrtv.app.ui.home.HomeScreen
import ua.ukrtv.app.ui.search.SearchScreen
import ua.ukrtv.app.ui.player.PlayerScreen
import ua.ukrtv.app.ui.settings.SettingsScreen
import ua.ukrtv.app.ui.top200.Top200Screen
import ua.ukrtv.app.ui.trends.FullTrendsGridScreen
import ua.ukrtv.app.ui.category.FullCategoryGridScreen
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.UkrtvTheme
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.util.PerformancePreferences
import ua.ukrtv.app.ui.splash.SplashScreen
import ua.ukrtv.app.ui.theme.BrandBlue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var performancePreferences: PerformancePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        val t0 = System.nanoTime()

        val providerColor = try {
            val prefs = getSharedPreferences("home_prefs", MODE_PRIVATE)
            val providerName = prefs.getString("default_provider", "Eneyida") ?: "Eneyida"
            val hex = when (providerName) {
                "Uakino" -> "#ca563f"
                else -> "#31C469"
            }
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            androidx.compose.ui.graphics.Color(0xFF31C469)
        }

        window.decorView.keepScreenOn = true
        window.setBackgroundDrawable(null)
        super.onCreate(savedInstanceState)
        if (ua.ukrtv.app.BuildConfig.DEBUG) {
            AppLogger.d("Startup", "Activity super.onCreate: ${(System.nanoTime() - t0) / 1_000_000}ms")
        }
        val formFactor = detectFormFactor(this)
        isTv = formFactor == FormFactor.TV
        requestedOrientation = if (isTv) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        setContent {
            UkrtvTheme(performancePreferences = performancePreferences, formFactor = formFactor) {
                var showMain by remember { mutableStateOf(false) }

                if (showMain) {
                    UkrtvTVApp()
                } else {
                    SplashScreen(
                        providerColor = providerColor,
                        onSplashFinished = { showMain = true }
                    )
                }
            }
        }
        if (ua.ukrtv.app.BuildConfig.DEBUG) {
            window.decorView.viewTreeObserver.addOnPreDrawListener(
                object : android.view.ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                        AppLogger.d("Startup", "First pre-draw: ${(System.nanoTime() - t0) / 1_000_000}ms")
                        return true
                    }
                }
            )
            AppLogger.d("Startup", "setContent done: ${(System.nanoTime() - t0) / 1_000_000}ms")
        }
    }

    private var isTv: Boolean = false

    override fun onStart() {
        super.onStart()
        window.decorView.keepScreenOn = true
    }

    override fun onStop() {
        super.onStop()
        window.decorView.keepScreenOn = false
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU) {
            ua.ukrtv.app.util.AppLogger.d("MainActivity", "Menu button pressed - Quick Settings trigger")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UkrtvTVApp() {
    val navController = rememberNavController()

    val onMovieClick = remember(navController) {
        { movie: ua.ukrtv.app.domain.model.Movie ->
            ua.ukrtv.app.util.AppLogger.d("Navigation", "home→detail: movie=${movie.title} url=${movie.pageUrl.take(40)}")
            navController.navigate(AppNavigation.detailRoute(movie.id, movie.pageUrl, movie.alternatePageUrl)) {
                launchSingleTop = true
            }
        }
    }
    val onContinueWatchingClick = remember(navController) {
        { movie: ua.ukrtv.app.domain.model.Movie ->
            ua.ukrtv.app.util.AppLogger.d("Navigation", "home→player (continue): movie=${movie.title}")
            navController.navigate(
                AppNavigation.playerRoute(
                    id = movie.id,
                    title = movie.title,
                    url = movie.pageUrl,
                    poster = movie.poster,
                    season = movie.season,
                    episode = movie.episode,
                    brandColor = movie.brandColor
                )
            ) {
                launchSingleTop = true
            }
        }
    }
    val onSearchClick = remember(navController) {
        { navController.navigate(AppNavigation.searchRoute()) { launchSingleTop = true } }
    }

    val deviceClass = LocalDeviceClass.current
    val navEnterDur = remember(deviceClass) {
        when (deviceClass) { DeviceClass.LOW -> 0; DeviceClass.MID -> 200; DeviceClass.HIGH -> 400 }
    }
    val navExitDur = remember(deviceClass) {
        when (deviceClass) { DeviceClass.LOW -> 0; DeviceClass.MID -> 200; DeviceClass.HIGH -> 300 }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = Color.Transparent)
    ) {
        NavHost(
            navController = navController,
            startDestination = AppNavigation.HOME,
            enterTransition = {
                if (targetState.destination.route == AppNavigation.SETTINGS) {
                    fadeIn(tween(navEnterDur))
                } else {
                    slideInHorizontally(tween(navEnterDur)) { it }
                }
            },
            exitTransition = {
                if (targetState.destination.route == AppNavigation.SETTINGS || initialState.destination.route == AppNavigation.SETTINGS) {
                    fadeOut(tween(navExitDur))
                } else {
                    slideOutHorizontally(tween(navExitDur)) { -it / 3 }
                }
            },
            popEnterTransition = {
                if (initialState.destination.route == AppNavigation.SETTINGS) {
                    fadeIn(tween(navEnterDur))
                } else {
                    slideInHorizontally(tween(navEnterDur)) { -it / 3 }
                }
            },
            popExitTransition = {
                if (initialState.destination.route == AppNavigation.SETTINGS) {
                    fadeOut(tween(navExitDur))
                } else {
                    slideOutHorizontally(tween(navExitDur)) { it }
                }
            }
        ) {
            composable(AppNavigation.HOME) {
                HomeScreen(
                    onMovieClick = onMovieClick,
                    onContinueWatchingClick = onContinueWatchingClick,
                    onSearchClick = onSearchClick,
                    onSearchQueryClick = { query -> navController.navigate(AppNavigation.searchRoute(query)) { launchSingleTop = true } },
                    onTop200Click = { navController.navigate(AppNavigation.TOP_200) { launchSingleTop = true } },
                    onSeeAllTrendsClick = { navController.navigate(AppNavigation.TRENDS_GRID) { launchSingleTop = true } },
                    onSeeAllCategoryClick = { categoryKey -> navController.navigate(AppNavigation.categoryGridRoute(categoryKey)) { launchSingleTop = true } },
                    onSettingsClick = { navController.navigate(AppNavigation.SETTINGS) { launchSingleTop = true } }
                )
            }
            composable(AppNavigation.TOP_200) {
                Top200Screen(
                    onMovieClick = { movie ->
                        navController.navigate(
                            AppNavigation.searchRoute(
                                movie.searchQueries.firstOrNull() ?: movie.title
                            )
                        ) { launchSingleTop = true }
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
                        navController.navigate(AppNavigation.detailRoute(movie.id, movie.pageUrl, movie.alternatePageUrl)) { launchSingleTop = true }
                    }
                )
            }
            composable(
                route = AppNavigation.DETAIL,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType },
                    navArgument("alternate") { type = NavType.StringType; defaultValue = "" }
                )
            ) {
                DetailScreen(
                    onMovieClick = onMovieClick,
                    onPlayClick = { launchState ->
                        if (launchState is ua.ukrtv.app.domain.model.MediaLaunchState.Ready) {
                            navController.navigate(
                                AppNavigation.playerRoute(
                                    launchState.contentId,
                                    launchState.title,
                                    url = launchState.streamResult.sourcePageUrl,
                                    poster = launchState.posterUrl,
                                    season = launchState.season,
                                    episode = launchState.episode,
                                    brandColor = launchState.brandColor
                                )
                            ) { launchSingleTop = true }
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = AppNavigation.CATEGORY_GRID,
                arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) {
                FullCategoryGridScreen(
                    onMovieClick = { movie: ua.ukrtv.app.domain.model.Movie ->
                        navController.navigate(AppNavigation.detailRoute(movie.id, movie.pageUrl, movie.alternatePageUrl)) { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppNavigation.TRENDS_GRID) {
                FullTrendsGridScreen(
                    onMovieClick = { movie: ua.ukrtv.app.domain.model.Movie ->
                        navController.navigate(AppNavigation.detailRoute(movie.id, movie.pageUrl, movie.alternatePageUrl)) { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppNavigation.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
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
                        navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                        navArgument("brandColor") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id") ?: ""
                    val title = backStackEntry.arguments?.getString("title") ?: ""
                    val url = backStackEntry.arguments?.getString("url") ?: ""
                    val poster = backStackEntry.arguments?.getString("poster") ?: ""
                    val season = backStackEntry.arguments?.getInt("season")?.takeIf { it != -1 }
                    val episode = backStackEntry.arguments?.getInt("episode")?.takeIf { it != -1 }
                    val brandColorStr = backStackEntry.arguments?.getString("brandColor")
                    val brandColor = remember(brandColorStr) {
                        if (brandColorStr.isNullOrBlank()) BrandBlue
                        else try { Color(android.graphics.Color.parseColor(brandColorStr)) } catch (_: Exception) { BrandBlue }
                    }

                    @OptIn(UnstableApi::class)
                    PlayerScreen(
                        url = url,
                        contentId = id,
                        title = title,
                        poster = poster,
                        season = season,
                        episode = episode,
                        brandColor = brandColor,
                        onBack = { navController.popBackStack() }
                    )
                }
        }
    }
}
