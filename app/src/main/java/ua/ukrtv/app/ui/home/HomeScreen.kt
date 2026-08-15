package ua.ukrtv.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.LocalFormFactor
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.Shapes
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.util.maxPostersPerRow
import ua.ukrtv.app.ui.home.components.BottomNavBar
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import ua.ukrtv.app.ui.home.components.ContentRow
import ua.ukrtv.app.ui.home.components.HeroCarousel
import ua.ukrtv.app.ui.home.components.PhoneHeroSection
import ua.ukrtv.app.ui.home.components.Top200SignatureHero
import ua.ukrtv.app.ui.home.components.TopBar
import ua.ukrtv.app.ui.home.components.LogoLockup
import ua.ukrtv.app.ui.home.components.ProviderChip
import ua.ukrtv.app.ui.home.components.SearchAction
import ua.ukrtv.app.ui.home.components.HomeBackground
import ua.ukrtv.app.ui.theme.*
import ua.ukrtv.app.ui.theme.PosterStyle
import ua.ukrtv.app.ui.theme.ProviderSizes
import ua.ukrtv.app.ui.home.components.TrendsTrailingButton
import ua.ukrtv.app.util.HomeLayout
private const val FOCUS_RESTORE_WINDOW_MS = 1500L

@Stable
private data class HomeScreenState(
    val isLoading: Boolean,
    val isCategoriesLoading: Boolean,
    val gridError: String?,
    val isOnline: Boolean,
    val top200Banners: List<Top200Movie>,
    val bannerMovies: List<Movie>,
    val continueWatching: List<Movie>,
    val watchlist: List<Movie>,
    val homeTrending: List<Movie>,
    val trendingLabel: String,
    val homeLayout: HomeLayout,
    val categoryMovies: List<Movie>,
    val categorySeries: List<Movie>,
    val categoryAnime: List<Movie>,
    val categoryCartoons: List<Movie>,
    val categoryCartoonSeries: List<Movie>,
    val activeBannerMovie: Top200Movie?,
    val providerColor: Color,
    val focusColor: Color,
    val bannerFocusRequester: FocusRequester,
    val currentProviderId: String
)

private data class HomeScreenActions(
    val onRetryGrid: () -> Unit,
    val onSearchClick: () -> Unit,
    val onMovieClick: (Movie) -> Unit,
    val onContinueWatchingClick: (Movie) -> Unit,
    val onTop200ItemClick: (Top200Movie) -> Unit,
    val onTop200Click: () -> Unit,
    val onMovieFocused: (Movie) -> Unit,
    val onActiveColorChange: (Color) -> Unit,
    val onDismissItem: (Movie) -> Unit,
    val onActiveMovieChange: (Top200Movie) -> Unit,
    val onSeeAllTrendsClick: () -> Unit,
    val onSeeAllCategoryClick: (String) -> Unit,
    val onSettingsClick: () -> Unit
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    onMovieClick: (Movie) -> Unit,
    onContinueWatchingClick: (Movie) -> Unit = onMovieClick,
    onSearchClick: () -> Unit,
    onSearchQueryClick: (String) -> Unit,
    onTop200Click: () -> Unit,
    onTop200ItemClick: (Top200Movie) -> Unit,
    onSeeAllTrendsClick: () -> Unit,
    onSeeAllCategoryClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onUpdateClick: () -> Unit = onSettingsClick,
    onHomeContentReady: () -> Unit = {}
) {
    val formFactor = LocalFormFactor.current
    if (formFactor == FormFactor.PHONE) {
        PhoneHomeScreen(viewModel, sharedTransitionScope, animatedContentScope, onMovieClick, onContinueWatchingClick, onSearchClick, onSearchQueryClick, onTop200Click, onSettingsClick, onSeeAllTrendsClick, onSeeAllCategoryClick, onHomeContentReady, onUpdateClick)
    } else {
        TvHomeScreen(viewModel, sharedTransitionScope, animatedContentScope, onMovieClick, onContinueWatchingClick, onSearchClick, onSearchQueryClick, onTop200Click, onTop200ItemClick, onSeeAllTrendsClick, onSeeAllCategoryClick, onSettingsClick, onHomeContentReady, onUpdateClick)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvHomeScreen(
    viewModel: HomeViewModel,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    onMovieClick: (Movie) -> Unit,
    onContinueWatchingClick: (Movie) -> Unit,
    onSearchClick: () -> Unit,
    onSearchQueryClick: (String) -> Unit,
    onTop200Click: () -> Unit,
    onTop200ItemClick: (Top200Movie) -> Unit,
    onSeeAllTrendsClick: () -> Unit,
    onSeeAllCategoryClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onHomeContentReady: () -> Unit = {},
    onUpdateClick: () -> Unit = onSettingsClick
) {
    val mainState by viewModel.mainContentState.collectAsStateWithLifecycle()
    val categoriesState by viewModel.categoriesState.collectAsStateWithLifecycle()
    val heroState by viewModel.heroState.collectAsStateWithLifecycle()
    val configState by viewModel.configState.collectAsStateWithLifecycle()
    val focusColor by viewModel.focusColor.collectAsStateWithLifecycle()
    val focusedMovie by viewModel.focusedMovie.collectAsStateWithLifecycle()
    val newUpdate by viewModel.newUpdate.collectAsStateWithLifecycle()

    val deviceClass = LocalDeviceClass.current
    val maxItems = deviceClass.maxPostersPerRow()

    var readyNotified by remember { mutableStateOf(false) }
    val mainReady = !mainState.isLoading || mainState.gridError != null
    LaunchedEffect(mainReady, categoriesState.isLoading) {
        if (mainReady && !categoriesState.isLoading && !readyNotified) {
            readyNotified = true
            onHomeContentReady()
        }
    }
    
    val providerColor = remember(configState.brandColor) { Color(configState.brandColor) }
    val bannerFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    
    var activeBannerMovie by remember(heroState.top200Banners) { 
        mutableStateOf(heroState.top200Banners.firstOrNull()) 
    }
    val scrollState = rememberLazyListState()
    
    val scrollFraction by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex > 0) 1f
            else (scrollState.firstVisibleItemScrollOffset / 1000f).coerceIn(0f, 1f)
        }
    }

    // Once scrolled past the hero, the background follows the focused poster (Netflix-style).
    val pastHero by remember {
        derivedStateOf { scrollFraction > 0.25f }
    }
    val heroBackdropColor = activeBannerMovie?.accentColor?.let { try { Color(android.graphics.Color.parseColor(it)) } catch(_: Exception) { Color.Unspecified } } ?: Color.Unspecified

    val homeState = HomeScreenState(
        isLoading = mainState.isLoading,
        isCategoriesLoading = categoriesState.isLoading,
        gridError = mainState.gridError,
        isOnline = configState.isOnline,
        top200Banners = heroState.top200Banners,
        bannerMovies = heroState.bannerMovies,
        continueWatching = mainState.continueWatching.take(maxItems),
        watchlist = mainState.watchlist.take(maxItems),
        homeTrending = mainState.homeTrending.take(maxItems),
        trendingLabel = configState.trendingLabel,
        homeLayout = configState.homeLayout,
        categoryMovies = categoriesState.categoryMovies.take(maxItems),
        categorySeries = categoriesState.categorySeries.take(maxItems),
        categoryAnime = categoriesState.categoryAnime.take(maxItems),
        categoryCartoons = categoriesState.categoryCartoons.take(maxItems),
        categoryCartoonSeries = categoriesState.categoryCartoonSeries.take(maxItems),
        activeBannerMovie = activeBannerMovie,
        providerColor = providerColor,
        focusColor = focusColor,
        bannerFocusRequester = bannerFocusRequester,
        currentProviderId = configState.currentProviderId
    )

    val actions = HomeScreenActions(
        onRetryGrid = { viewModel.retryGrid() },
        onSearchClick = onSearchClick,
        onMovieClick = onMovieClick,
        onContinueWatchingClick = onContinueWatchingClick,
        onTop200ItemClick = onTop200ItemClick,
        onTop200Click = onTop200Click,
        onMovieFocused = { viewModel.onMovieFocused(it, context) },
        onActiveColorChange = { viewModel.provideFocusColor(it) },
        onDismissItem = { viewModel.dismissContinueWatching(it) },
        onActiveMovieChange = { activeBannerMovie = it },
        onSeeAllTrendsClick = onSeeAllTrendsClick,
        onSeeAllCategoryClick = onSeeAllCategoryClick,
        onSettingsClick = onSettingsClick
    )

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        HomeBackground(
            focusedColor = focusColor,
            brandColor = providerColor,
            backdropColor = if (pastHero) focusColor else heroBackdropColor,
            backdropUrl = if (pastHero) focusedMovie?.poster else activeBannerMovie?.backdropUrl,
            backdropBlur = if (pastHero && deviceClass == DeviceClass.HIGH) 28.dp else 0.dp,
            scrollFraction = { scrollFraction }
        ) {
            HomeScreenContent(
                state = homeState,
                actions = actions,
                scrollState = scrollState,
                restoreMovie = focusedMovie,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope
            )
        }

        newUpdate?.let { info ->
            AlertDialog(
                onDismissRequest = { viewModel.skipUpdate(info.versionCode) },
                title = { Text("Доступна нова версія ${info.versionName}") },
                text = {
                    Column {
                        Text(info.changelog)
                        if (info.versionCode > ua.ukrtv.app.BuildConfig.VERSION_CODE + 5) {
                            Spacer(Modifier.height(8.dp))
                            Text("Рекомендується термінове оновлення!", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onUpdateClick() },
                        colors = ButtonDefaults.buttonColors(containerColor = providerColor)
                    ) {
                        Text("Оновити")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.skipUpdate(info.versionCode) }) {
                        Text("Пізніше", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Background,
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f),
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.widthIn(max = 500.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    state: HomeScreenState,
    actions: HomeScreenActions,
    scrollState: LazyListState = rememberLazyListState(),
    restoreMovie: Movie? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null
) {
    val (isLoading, isCategoriesLoading, gridError, isOnline, top200Banners, bannerMovies, continueWatching, watchlist, homeTrending, trendingLabel, homeLayout, categoryMovies, categorySeries, categoryAnime, categoryCartoons, categoryCartoonSeries, activeBannerMovie, providerColor, focusColor, bannerFocusRequester, currentProviderId) = state
    val (onRetryGrid, onSearchClick, onMovieClick, onContinueWatchingClick, onTop200ItemClick, onTop200Click, onMovieFocused, onActiveColorChange, onDismissItem, onActiveMovieChange, onSeeAllTrendsClick, onSeeAllCategoryClick, onSettingsClick) = actions

    val restoreTarget = remember { restoreMovie }
    var restoreWindowOpen by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(FOCUS_RESTORE_WINDOW_MS)
        restoreWindowOpen = false
    }

    // Rail Fade: track which row currently owns focus so the others can dim.
    var focusedRowId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = scrollState,
        contentPadding = PaddingValues(bottom = GridDefaults.contentBottomPadding)
    ) {
        item(key = "top_bar") {
            Box(modifier = Modifier.onFocusChanged { if (it.isFocused) focusedRowId = null }) {
                TopBar(
                    currentProviderId = currentProviderId,
                    brandColor = providerColor,
                    onSearchClick = onSearchClick,
                    onSettingsClick = onSettingsClick
                )
            }
        }

        if (!isOnline) {
            item(key = "offline_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GridDefaults.horizontalPadding, vertical = 16.dp)
                        .background(Color(0xFFE53935).copy(alpha = 0.8f), Shapes.card)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TvText("Відсутнє підключення до мережі. Відображення кешованих даних.", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item(key = "hero_carousel") {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).onFocusChanged { if (it.isFocused) focusedRowId = null }) {
                if (top200Banners.isNotEmpty()) {
                    Top200SignatureHero(
                        items = top200Banners,
                        brandColor = providerColor,
                        onItemClick = onTop200ItemClick,
                        onItemLongClick = { onTop200Click() },
                        onActiveMovieChange = onActiveMovieChange,
                        modifier = Modifier.focusRequester(bannerFocusRequester)
                    )
                } else if (bannerMovies.isNotEmpty()) {
                    HeroCarousel(
                        items = bannerMovies,
                        brandColor = providerColor,
                        onWatchClick = onMovieClick,
                        onActiveColorChange = onActiveColorChange,
                        modifier = Modifier.focusRequester(bannerFocusRequester)
                    )
                }
            }
        }

        if (homeLayout.showContinueWatching && (continueWatching.isNotEmpty() || isLoading)) {
            item(key = "continue_watching", contentType = "content_row") {
                ContentRow("Продовжити перегляд", continueWatching, providerColor, onContinueWatchingClick, onDismissItem, onMovieFocused, isLoading = isLoading, sharedTransitionScope = sharedTransitionScope, animatedContentScope = animatedContentScope, providerHint = currentProviderId, restoreMovie = restoreTarget, restoreWindowOpen = { restoreWindowOpen }, onRestoreHandled = { restoreWindowOpen = false }, rowId = "continue_watching", focusedRowId = focusedRowId, onRowFocused = { focusedRowId = "continue_watching" })
            }
        }

        if (homeLayout.showWatchlist && (watchlist.isNotEmpty() || isLoading)) {
            item(key = "watchlist", contentType = "content_row") {
                ContentRow("Мій список", watchlist, providerColor, onMovieClick, null, onMovieFocused, isLoading = isLoading, sharedTransitionScope = sharedTransitionScope, animatedContentScope = animatedContentScope, providerHint = currentProviderId, restoreMovie = restoreTarget, restoreWindowOpen = { restoreWindowOpen }, onRestoreHandled = { restoreWindowOpen = false }, rowId = "watchlist", focusedRowId = focusedRowId, onRowFocused = { focusedRowId = "watchlist" })
            }
        }

        if (homeLayout.showTrends && (homeTrending.isNotEmpty() || isLoading)) {
            item(key = "trending", contentType = "content_row") {
                ContentRow(
                    title = trendingLabel,
                    items = homeTrending,
                    brandColor = providerColor,
                    onItemClick = onMovieClick,
                    onItemDismiss = null,
                    onItemFocused = onMovieFocused,
                    useLargeCards = true,
                    isLoading = isLoading,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    providerHint = currentProviderId,
                    restoreMovie = restoreTarget,
                    restoreWindowOpen = { restoreWindowOpen },
                    onRestoreHandled = { restoreWindowOpen = false },
                    rowId = "trending", focusedRowId = focusedRowId, onRowFocused = { focusedRowId = "trending" },
                    trailingContent = {
                        val provider = homeTrending.firstOrNull()?.provider ?: currentProviderId
                        TrendsTrailingButton(
                            brandColor = providerColor,
                            onClick = onSeeAllTrendsClick,
                            useLargeCards = true,
                            provider = provider
                        )
                    }
                )
            }
        }

        if (homeLayout.showMovies && (categoryMovies.isNotEmpty() || isCategoriesLoading)) {
            item(key = "category_movies", contentType = "content_row") {
                ContentRow(
                    title = "Фільми",
                    items = categoryMovies,
                    brandColor = providerColor,
                    onItemClick = onMovieClick,
                    onItemDismiss = null,
                    onItemFocused = onMovieFocused,
                    isLoading = isCategoriesLoading,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    providerHint = currentProviderId,
                    restoreMovie = restoreTarget,
                    restoreWindowOpen = { restoreWindowOpen },
                    onRestoreHandled = { restoreWindowOpen = false },
                    rowId = "category_movies", focusedRowId = focusedRowId, onRowFocused = { focusedRowId = "category_movies" },
                    trailingContent = {
                        val provider = categoryMovies.firstOrNull()?.provider ?: currentProviderId
                        TrendsTrailingButton(brandColor = providerColor, onClick = { onSeeAllCategoryClick("movies") }, provider = provider)
                    }
                )
            }
        }

        if (homeLayout.showSeries && (categorySeries.isNotEmpty() || isCategoriesLoading)) {
            item(key = "category_series", contentType = "content_row") {
                ContentRow(
                    title = "Серіали",
                    items = categorySeries,
                    brandColor = providerColor,
                    onItemClick = onMovieClick,
                    onItemDismiss = null,
                    onItemFocused = onMovieFocused,
                    isLoading = isCategoriesLoading,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    providerHint = currentProviderId,
                    restoreMovie = restoreTarget,
                    restoreWindowOpen = { restoreWindowOpen },
                    onRestoreHandled = { restoreWindowOpen = false },
                    rowId = "category_series", focusedRowId = focusedRowId, onRowFocused = { focusedRowId = "category_series" },
                    trailingContent = {
                        val provider = categorySeries.firstOrNull()?.provider ?: currentProviderId
                        TrendsTrailingButton(brandColor = providerColor, onClick = { onSeeAllCategoryClick("series") }, provider = provider)
                    }
                )
            }
        }

        if (homeLayout.showAnime && (categoryAnime.isNotEmpty() || isCategoriesLoading)) {
            item(key = "category_anime", contentType = "content_row") {
                ContentRow(
                    title = "Аніме",
                    items = categoryAnime,
                    brandColor = providerColor,
                    onItemClick = onMovieClick,
                    onItemDismiss = null,
                    onItemFocused = onMovieFocused,
                    isLoading = isCategoriesLoading,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    providerHint = currentProviderId,
                    restoreMovie = restoreTarget,
                    restoreWindowOpen = { restoreWindowOpen },
                    onRestoreHandled = { restoreWindowOpen = false },
                    rowId = "category_anime", focusedRowId = focusedRowId, onRowFocused = { focusedRowId = "category_anime" },
                    trailingContent = {
                        val provider = categoryAnime.firstOrNull()?.provider ?: currentProviderId
                        TrendsTrailingButton(brandColor = providerColor, onClick = { onSeeAllCategoryClick("anime") }, provider = provider)
                    }
                )
            }
        }

        if (homeLayout.showCartoons && (categoryCartoons.isNotEmpty() || isCategoriesLoading)) {
            item(key = "category_cartoons", contentType = "content_row") {
                ContentRow(
                    title = "Мультфільми",
                    items = categoryCartoons,
                    brandColor = providerColor,
                    onItemClick = onMovieClick,
                    onItemDismiss = null,
                    onItemFocused = onMovieFocused,
                    isLoading = isCategoriesLoading,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    providerHint = currentProviderId,
                    restoreMovie = restoreTarget,
                    restoreWindowOpen = { restoreWindowOpen },
                    onRestoreHandled = { restoreWindowOpen = false },
                    rowId = "category_cartoons", focusedRowId = focusedRowId, onRowFocused = { focusedRowId = "category_cartoons" },
                    trailingContent = {
                        val provider = categoryCartoons.firstOrNull()?.provider ?: currentProviderId
                        TrendsTrailingButton(brandColor = providerColor, onClick = { onSeeAllCategoryClick("cartoons") }, provider = provider)
                    }
                )
            }
        }

        if (homeLayout.showCartoonSeries && (categoryCartoonSeries.isNotEmpty() || isCategoriesLoading)) {
            item(key = "category_cartoon_series", contentType = "content_row") {
                ContentRow(
                    title = "Мультсеріали",
                    items = categoryCartoonSeries,
                    brandColor = providerColor,
                    onItemClick = onMovieClick,
                    onItemDismiss = null,
                    onItemFocused = onMovieFocused,
                    isLoading = isCategoriesLoading,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    providerHint = currentProviderId,
                    restoreMovie = restoreTarget,
                    restoreWindowOpen = { restoreWindowOpen },
                    onRestoreHandled = { restoreWindowOpen = false },
                    rowId = "category_cartoon_series", focusedRowId = focusedRowId, onRowFocused = { focusedRowId = "category_cartoon_series" },
                    trailingContent = {
                        val provider = categoryCartoonSeries.firstOrNull()?.provider ?: currentProviderId
                        TrendsTrailingButton(brandColor = providerColor, onClick = { onSeeAllCategoryClick("cartoon_series") }, provider = provider)
                    }
                )
            }
        }

        if (!isLoading && gridError != null) {
            item(key = "grid_error", contentType = "error") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TvText(
                        text = "Помилка завантаження:\n$gridError",
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    TvButton(
                        onClick = onRetryGrid,
                        colors = TvButtonDefaults.colors(
                            containerColor = providerColor,
                            contentColor = Color.White
                        )
                    ) {
                        TvText("Спробувати знову", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PhoneHomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    onMovieClick: (Movie) -> Unit,
    onContinueWatchingClick: (Movie) -> Unit = onMovieClick,
    onSearchClick: () -> Unit,
    onSearchQueryClick: (String) -> Unit = { onSearchClick() },
    onTop200Click: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onSeeAllTrendsClick: () -> Unit = {},
    onSeeAllCategoryClick: (String) -> Unit = {},
    onHomeContentReady: () -> Unit = {},
    onUpdateClick: () -> Unit = onSettingsClick
) {
    val gridState = rememberLazyListState()
    val density = LocalDensity.current
    val mainState by viewModel.mainContentState.collectAsStateWithLifecycle()
    val categoriesState by viewModel.categoriesState.collectAsStateWithLifecycle()
    val heroState by viewModel.heroState.collectAsStateWithLifecycle()
    val configState by viewModel.configState.collectAsStateWithLifecycle()
    val newUpdate by viewModel.newUpdate.collectAsStateWithLifecycle()

    var readyNotified by remember { mutableStateOf(false) }
    val mainReady = !mainState.isLoading || mainState.gridError != null
    LaunchedEffect(mainReady, categoriesState.isLoading) {
        if (mainReady && !categoriesState.isLoading && !readyNotified) {
            readyNotified = true
            onHomeContentReady()
        }
    }

    val providerColor = remember(configState.brandColor) { Color(configState.brandColor) }
    var activeTop200Movie by remember { mutableStateOf<Top200Movie?>(null) }

    val deviceClass = LocalDeviceClass.current
    val maxItems = deviceClass.maxPostersPerRow()
    val continueWatching = remember(mainState.continueWatching, maxItems) { mainState.continueWatching.take(maxItems) }
    val watchlist = remember(mainState.watchlist, maxItems) { mainState.watchlist.take(maxItems) }
    val homeTrending = remember(mainState.homeTrending, maxItems) { mainState.homeTrending.take(maxItems) }
    val categoryMovies = remember(categoriesState.categoryMovies, maxItems) { categoriesState.categoryMovies.take(maxItems) }
    val categorySeries = remember(categoriesState.categorySeries, maxItems) { categoriesState.categorySeries.take(maxItems) }
    val categoryAnime = remember(categoriesState.categoryAnime, maxItems) { categoriesState.categoryAnime.take(maxItems) }
    val categoryCartoons = remember(categoriesState.categoryCartoons, maxItems) { categoriesState.categoryCartoons.take(maxItems) }
    val categoryCartoonSeries = remember(categoriesState.categoryCartoonSeries, maxItems) { categoriesState.categoryCartoonSeries.take(maxItems) }
    val scope = rememberCoroutineScope()

    val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp

    val scrollFraction by remember {
        val hPx = with(density) { (50 * screenHeightDp / 100).dp.toPx() }
        derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) 1f
            else (gridState.firstVisibleItemScrollOffset / hPx).coerceIn(0f, 1f)
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentRoute = "home",
                brandColor = providerColor,
                onHomeClick = {
                    scope.launch { gridState.animateScrollToItem(0) }
                },
                onSearchClick = onSearchClick,
                onMyListClick = { onSeeAllCategoryClick("watchlist") },
                onSettingsClick = onSettingsClick
            )
        },
        containerColor = Background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
                    .background(Background)
                    .statusBarsPadding()
            ) {
                // TopBar dock: UKRTV logo + provider chip + Search (settings live in bottom nav)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Background)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LogoLockup(brandColor = providerColor, fontSize = 18.dp)
                        Spacer(Modifier.width(10.dp))
                        ProviderChip(
                            providerName = configState.currentProviderId,
                            brandColor = providerColor,
                            fontSize = 11.dp,
                            compact = true
                        )
                        Spacer(Modifier.weight(1f))
                        SearchAction(
                            brandColor = providerColor,
                            onClick = onSearchClick,
                            compact = true
                        )
                    }
                }

                val pullRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    state = pullRefreshState,
                    isRefreshing = mainState.isLoading,
                    onRefresh = { viewModel.retryGrid() },
                    modifier = Modifier.weight(1f)
                ) {
                    // Scrollable content — fills remaining space below header
                    LazyColumn(
                        state = gridState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Hero section (Top 200 carousel)
                        item(key = "hero", contentType = "hero") {
                            if (heroState.top200Banners.isNotEmpty()) {
                                PhoneHeroSection(
                                    items = heroState.top200Banners,
                                    brandColor = providerColor,
                                    onItemClick = { movie -> onSearchQueryClick(movie.title) },
                                    onActiveMovieChange = { activeTop200Movie = it },
                                    scrollFraction = { scrollFraction },
                                    screenHeightDp = screenHeightDp.toFloat()
                                )
                            }
                        }

                        if (!configState.isOnline) {
                            item(key = "offline", contentType = "banner") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFFE53935)).padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⚠ Немає підключення", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }

                        // Content rows
                        if (configState.homeLayout.showContinueWatching && (continueWatching.isNotEmpty() || mainState.isLoading)) {
                            item(key = "continue", contentType = "content_row") {
                                ContentRow("Продовжити перегляд", continueWatching, providerColor, onContinueWatchingClick, isLoading = mainState.isLoading, sharedTransitionScope = sharedTransitionScope, animatedContentScope = animatedContentScope, providerHint = configState.currentProviderId)
                            }
                        }

                        if (configState.homeLayout.showWatchlist && (watchlist.isNotEmpty() || mainState.isLoading)) {
                            item(key = "watchlist", contentType = "content_row") {
                                ContentRow("Мій список", watchlist, providerColor, onMovieClick, isLoading = mainState.isLoading, sharedTransitionScope = sharedTransitionScope, animatedContentScope = animatedContentScope, providerHint = configState.currentProviderId)
                            }
                        }

                        if (configState.homeLayout.showTrends && (homeTrending.isNotEmpty() || mainState.isLoading)) {
                            item(key = "trending", contentType = "content_row") {
                                val provider = homeTrending.firstOrNull()?.provider ?: configState.currentProviderId
                                val posterStyle = PosterStyle.forProvider(provider)
                                val phoneDims = ProviderSizes.phoneCard(posterStyle)
                                
                                ContentRow(
                                    configState.trendingLabel,
                                    homeTrending,
                                    providerColor,
                                    onMovieClick,
                                    useLargeCards = true,
                                    isLoading = mainState.isLoading,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedContentScope = animatedContentScope,
                                    providerHint = configState.currentProviderId,
                                    trailingContent = {
                                        Box(
                                            modifier = Modifier
                                                .width(phoneDims.width)
                                                .height(phoneDims.height)
                                                .clip(Shapes.card)
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .clickable { onSeeAllTrendsClick() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Усі тренди",
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        if (configState.homeLayout.showMovies && (categoryMovies.isNotEmpty() || categoriesState.isLoading)) {
                            item(key = "cat_movies", contentType = "content_row") {
                                val provider = categoryMovies.firstOrNull()?.provider ?: configState.currentProviderId
                                val posterStyle = PosterStyle.forProvider(provider)
                                val phoneDims = ProviderSizes.phoneCard(posterStyle)
                                
                                ContentRow(
                                    "Фільми", categoryMovies, providerColor, onMovieClick,
                                    isLoading = categoriesState.isLoading,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedContentScope = animatedContentScope,
                                    providerHint = configState.currentProviderId,
                                    trailingContent = {
                                        Box(
                                            modifier = Modifier
                                                .width(phoneDims.width)
                                                .height(phoneDims.height)
                                                .clip(Shapes.card)
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .clickable { onSeeAllCategoryClick("movies") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Усі фільми", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
                                        }
                                    }
                                )
                            }
                        }

                        if (configState.homeLayout.showSeries && (categorySeries.isNotEmpty() || categoriesState.isLoading)) {
                            item(key = "cat_series", contentType = "content_row") {
                                val provider = categorySeries.firstOrNull()?.provider ?: configState.currentProviderId
                                val posterStyle = PosterStyle.forProvider(provider)
                                val phoneDims = ProviderSizes.phoneCard(posterStyle)
                                
                                ContentRow(
                                    "Серіали", categorySeries, providerColor, onMovieClick,
                                    isLoading = categoriesState.isLoading,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedContentScope = animatedContentScope,
                                    providerHint = configState.currentProviderId,
                                    trailingContent = {
                                        Box(
                                            modifier = Modifier
                                                .width(phoneDims.width)
                                                .height(phoneDims.height)
                                                .clip(Shapes.card)
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .clickable { onSeeAllCategoryClick("series") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Усі серіали", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
                                        }
                                    }
                                )
                            }
                        }

                        if (configState.homeLayout.showAnime && (categoryAnime.isNotEmpty() || categoriesState.isLoading)) {
                            item(key = "cat_anime", contentType = "content_row") {
                                val provider = categoryAnime.firstOrNull()?.provider ?: configState.currentProviderId
                                val posterStyle = PosterStyle.forProvider(provider)
                                val phoneDims = ProviderSizes.phoneCard(posterStyle)
                                
                                ContentRow(
                                    "Аніме", categoryAnime, providerColor, onMovieClick,
                                    isLoading = categoriesState.isLoading,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedContentScope = animatedContentScope,
                                    providerHint = configState.currentProviderId,
                                    trailingContent = {
                                        Box(
                                            modifier = Modifier
                                                .width(phoneDims.width)
                                                .height(phoneDims.height)
                                                .clip(Shapes.card)
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .clickable { onSeeAllCategoryClick("anime") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Усе аніме", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
                                        }
                                    }
                                )
                            }
                        }

                        if (configState.homeLayout.showCartoons && (categoryCartoons.isNotEmpty() || categoriesState.isLoading)) {
                            item(key = "cat_cartoons", contentType = "content_row") {
                                val provider = categoryCartoons.firstOrNull()?.provider ?: configState.currentProviderId
                                val posterStyle = PosterStyle.forProvider(provider)
                                val phoneDims = ProviderSizes.phoneCard(posterStyle)
                                
                                ContentRow(
                                    "Мультфільми", categoryCartoons, providerColor, onMovieClick,
                                    isLoading = categoriesState.isLoading,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedContentScope = animatedContentScope,
                                    providerHint = configState.currentProviderId,
                                    trailingContent = {
                                        Box(
                                            modifier = Modifier
                                                .width(phoneDims.width)
                                                .height(phoneDims.height)
                                                .clip(Shapes.card)
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .clickable { onSeeAllCategoryClick("cartoons") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Усі мультфільми", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
                                        }
                                    }
                                )
                            }
                        }

                        if (configState.homeLayout.showCartoonSeries && (categoryCartoonSeries.isNotEmpty() || categoriesState.isLoading)) {
                            item(key = "cat_cartoon_series", contentType = "content_row") {
                                val provider = categoryCartoonSeries.firstOrNull()?.provider ?: configState.currentProviderId
                                val posterStyle = PosterStyle.forProvider(provider)
                                val phoneDims = ProviderSizes.phoneCard(posterStyle)
                                
                                ContentRow(
                                    "Мультсеріали", categoryCartoonSeries, providerColor, onMovieClick,
                                    isLoading = categoriesState.isLoading,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedContentScope = animatedContentScope,
                                    providerHint = configState.currentProviderId,
                                    trailingContent = {
                                        Box(
                                            modifier = Modifier
                                                .width(phoneDims.width)
                                                .height(phoneDims.height)
                                                .clip(Shapes.card)
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .clickable { onSeeAllCategoryClick("cartoon_series") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Усі мультсеріали", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
                                        }
                                    }
                                )
                            }
                        }

                        if (!mainState.isLoading && mainState.gridError != null) {
                            item(key = "grid_error", contentType = "error") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.BrokenImage,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(80.dp)
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = "Помилка завантаження:\n${mainState.gridError}",
                                        color = Color.White.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(32.dp))
                                    Button(
                                        onClick = { viewModel.retryGrid() },
                                        colors = ButtonDefaults.buttonColors(containerColor = providerColor)
                                    ) {
                                        Text("Спробувати знову", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (!mainState.isLoading && mainState.gridError == null && continueWatching.isEmpty() && watchlist.isEmpty() && homeTrending.isEmpty()) {
                            item(contentType = "empty") {
                                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Outlined.BrokenImage,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.3f),
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Нічого не знайдено",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            newUpdate?.let { info ->
                AlertDialog(
                    onDismissRequest = { viewModel.skipUpdate(info.versionCode) },
                    title = { Text("Оновлення ${info.versionName}") },
                    text = { Text(info.changelog) },
                    confirmButton = {
                        Button(
                            onClick = { onUpdateClick() },
                            colors = ButtonDefaults.buttonColors(containerColor = providerColor)
                        ) {
                            Text("Оновити")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.skipUpdate(info.versionCode) }) {
                            Text("Пропустити")
                        }
                    },
                    containerColor = Background,
                    titleContentColor = Color.White,
                    textContentColor = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
