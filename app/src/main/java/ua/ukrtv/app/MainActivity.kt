package ua.ukrtv.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ua.ukrtv.app.ui.theme.detectFormFactor
import ua.ukrtv.app.ui.theme.FormFactor
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import ua.ukrtv.app.ui.detail.DetailScreen
import ua.ukrtv.app.ui.home.HomeScreen
import ua.ukrtv.app.ui.search.SearchScreen
import ua.ukrtv.app.ui.player.PlayerScreen
import ua.ukrtv.app.ui.settings.SettingsScreen
import ua.ukrtv.app.ui.top200.Top200Screen
import ua.ukrtv.app.ui.trends.FullTrendsGridScreen
import ua.ukrtv.app.ui.category.FullCategoryGridScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.UkrtvTheme
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.util.PerformancePreferences
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.navigation.Screen
import ua.ukrtv.app.ui.splash.SplashScreen
import androidx.navigation.toRoute
import kotlinx.coroutines.delay

private const val MIN_SPLASH_MS = 1200L
private const val MAX_SPLASH_MS = 8000L

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var performancePreferences: PerformancePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        window.decorView.keepScreenOn = true
        super.onCreate(savedInstanceState)

        val providerColor = try {
            val prefs = getSharedPreferences("home_prefs", MODE_PRIVATE)
            val providerName = prefs.getString("default_provider", "Eneyida") ?: "Eneyida"
            val hex = when (providerName) {
                "Uakino" -> "#ca563f"
                else -> "#31C469"
            }
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            Color(0xFF31C469)
        }

        val formFactor = detectFormFactor(this)
        requestedOrientation = if (formFactor == FormFactor.TV) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        setContent {
            UkrtvTheme(performancePreferences = performancePreferences, formFactor = formFactor) {
                var showApp by remember { mutableStateOf(false) }
                var showSplash by remember { mutableStateOf(true) }
                var minDelayPassed by remember { mutableStateOf(false) }
                var homeReady by remember { mutableStateOf(false) }
                var forceDismiss by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    showApp = true
                }
                LaunchedEffect(Unit) {
                    delay(MIN_SPLASH_MS)
                    minDelayPassed = true
                }
                LaunchedEffect(Unit) {
                    delay(MAX_SPLASH_MS)
                    forceDismiss = true
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (showApp) {
                        UkrtvTVApp(formFactor, onHomeContentReady = { homeReady = true })
                    }
                    if (showSplash) {
                        SplashScreen(
                            providerColor = providerColor,
                            dismiss = (minDelayPassed && homeReady) || forceDismiss,
                            onFinished = { showSplash = false }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        window.decorView.keepScreenOn = true
    }

    override fun onStop() {
        super.onStop()
        window.decorView.keepScreenOn = false
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UkrtvTVApp(formFactor: FormFactor, onHomeContentReady: () -> Unit = {}) {
    val navController = rememberNavController()

    val deviceClass = LocalDeviceClass.current
    val navEnterDur = when (deviceClass) {
        DeviceClass.LOW -> 0
        DeviceClass.MID -> 200
        DeviceClass.HIGH -> 400
    }
    val navExitDur = when (deviceClass) {
        DeviceClass.LOW -> 0
        DeviceClass.MID -> 200
        DeviceClass.HIGH -> 300
    }

    val onMovieClick = remember(navController) {
        { movie: ua.ukrtv.app.domain.model.Movie ->
            navController.navigate(Screen.Detail(movie.id, movie.pageUrl, movie.alternatePageUrl)) {
                launchSingleTop = true
            }
        }
    }
    val onContinueWatchingClick = remember(navController) {
        { movie: ua.ukrtv.app.domain.model.Movie ->
            navController.navigate(
                Screen.Player(
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
        { navController.navigate(Screen.Search()) { launchSingleTop = true } }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = Color.Transparent)
    ) {
        SharedTransitionLayout {
            NavHost(
                navController = navController,
                startDestination = Screen.Home,
                enterTransition = { slideInHorizontally(tween(navEnterDur)) { it } },
                exitTransition = { slideOutHorizontally(tween(navExitDur)) { -it / 3 } },
                popEnterTransition = { slideInHorizontally(tween(navEnterDur)) { -it / 3 } },
                popExitTransition = { slideOutHorizontally(tween(navExitDur)) { it } }
            ) {
                composable<Screen.Home> {
                    HomeScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedContentScope = this@composable,
                        onMovieClick = onMovieClick,
                        onContinueWatchingClick = onContinueWatchingClick,
                        onSearchClick = onSearchClick,
                        onSearchQueryClick = { query -> navController.navigate(Screen.Search(query)) { launchSingleTop = true } },
                        onTop200Click = { navController.navigate(Screen.Top200) { launchSingleTop = true } },
                        onTop200ItemClick = { movie ->
                            navController.navigate(Screen.Search(movie.searchQueries.firstOrNull() ?: movie.title)) { launchSingleTop = true }
                        },
                        onSeeAllTrendsClick = { navController.navigate(Screen.TrendsGrid) { launchSingleTop = true } },
                        onSeeAllCategoryClick = { categoryKey -> navController.navigate(Screen.CategoryGrid(categoryKey)) { launchSingleTop = true } },
                        onSettingsClick = { navController.navigate(Screen.Settings) { launchSingleTop = true } },
                        onHomeContentReady = onHomeContentReady
                    )
                }
                composable<Screen.Top200> {
                    Top200Screen(
                        onMovieClick = { movie ->
                            navController.navigate(Screen.Search(movie.searchQueries.firstOrNull() ?: movie.title)) { launchSingleTop = true }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<Screen.Search> {
                    SearchScreen(
                        onMovieClick = { movie ->
                            navController.navigate(Screen.Detail(movie.id, movie.pageUrl, movie.alternatePageUrl)) { launchSingleTop = true }
                        }
                    )
                }
                composable<Screen.Detail> {
                    DetailScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedContentScope = this@composable,
                        onMovieClick = onMovieClick,
                        onPlayClick = { launchState ->
                            if (launchState is ua.ukrtv.app.domain.model.MediaLaunchState.Ready) {
                                navController.navigate(
                                    Screen.Player(
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
                composable<Screen.CategoryGrid> {
                    FullCategoryGridScreen(
                        onMovieClick = { movie ->
                            navController.navigate(Screen.Detail(movie.id, movie.pageUrl, movie.alternatePageUrl)) { launchSingleTop = true }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<Screen.TrendsGrid> {
                    FullTrendsGridScreen(
                        onMovieClick = { movie ->
                            navController.navigate(Screen.Detail(movie.id, movie.pageUrl, movie.alternatePageUrl)) { launchSingleTop = true }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<Screen.Settings> {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable<Screen.Player> { backStackEntry ->
                    val player: Screen.Player = backStackEntry.toRoute()
                    val brandColor = remember(player.brandColor) {
                        if (player.brandColor.isNullOrBlank()) BrandBlue
                        else try { Color(android.graphics.Color.parseColor(player.brandColor)) } catch (_: Exception) { BrandBlue }
                    }

                    PlayerScreen(
                        url = player.url,
                        contentId = player.id,
                        title = player.title,
                        poster = player.poster,
                        season = player.season,
                        episode = player.episode,
                        brandColor = brandColor,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
