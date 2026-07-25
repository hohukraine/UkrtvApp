package ua.ukrtv.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.view.Choreographer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.debounce
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.util.AppLogger
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.ui.home.components.BottomNavBar
import ua.ukrtv.app.util.DeviceClass
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import ua.ukrtv.app.ui.home.components.ContentRow
import ua.ukrtv.app.ui.home.components.HeroCarousel
import ua.ukrtv.app.ui.home.components.PhoneHeroSection
import ua.ukrtv.app.ui.home.components.PhoneProviderSwitcher
import ua.ukrtv.app.ui.home.components.Top200SignatureHero
import ua.ukrtv.app.ui.home.components.TopBar
import ua.ukrtv.app.ui.home.components.HomeBackground
import ua.ukrtv.app.ui.components.ShimmerBox
import ua.ukrtv.app.ui.theme.*
import ua.ukrtv.app.ui.home.components.TrendsTrailingButton
import ua.ukrtv.app.ui.home.MovieCard
import ua.ukrtv.app.util.HomeLayout

@Stable
private data class HomeScreenState(
    val isLoading: Boolean,
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
    val providers: List<ua.ukrtv.app.domain.model.Provider>,
    val currentProviderId: String,
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onMovieClick: (Movie) -> Unit,
    onContinueWatchingClick: (Movie) -> Unit = onMovieClick,
    onSearchClick: () -> Unit,
    onSearchQueryClick: (String) -> Unit = { onSearchClick() },
    onTop200Click: () -> Unit,
    onTop200ItemClick: (Top200Movie) -> Unit = {},
    onSeeAllTrendsClick: () -> Unit = {},
    onSeeAllCategoryClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val formFactor = LocalFormFactor.current
    when (formFactor) {
        FormFactor.TV -> TvHomeScreen(viewModel, onMovieClick, onContinueWatchingClick, onSearchClick, onSearchQueryClick, onTop200Click, onTop200ItemClick, onSeeAllTrendsClick, onSeeAllCategoryClick, onSettingsClick)
        FormFactor.PHONE, FormFactor.TABLET -> PhoneHomeScreen(viewModel, onMovieClick, onContinueWatchingClick, onSearchClick, onSearchQueryClick, onTop200Click, onSettingsClick, onSeeAllTrendsClick, onSeeAllCategoryClick)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvHomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onMovieClick: (Movie) -> Unit,
    onContinueWatchingClick: (Movie) -> Unit = onMovieClick,
    onSearchClick: () -> Unit,
    onSearchQueryClick: (String) -> Unit = { onSearchClick() },
    onTop200Click: () -> Unit,
    onTop200ItemClick: (Top200Movie) -> Unit = {},
    onSeeAllTrendsClick: () -> Unit = {},
    onSeeAllCategoryClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val mainState by viewModel.mainContentState.collectAsStateWithLifecycle()
    val categoriesState by viewModel.categoriesState.collectAsStateWithLifecycle()
    val heroState by viewModel.heroState.collectAsStateWithLifecycle()
    val configState by viewModel.configState.collectAsStateWithLifecycle()
    val focusColor by viewModel.focusColor.collectAsStateWithLifecycle()

    val deviceClass = LocalDeviceClass.current
    val bannerFocusRequester = remember { FocusRequester() }
    var activeBannerMovie by remember { mutableStateOf<Top200Movie?>(null) }
    val providerColor = remember(configState.brandColor) { Color(configState.brandColor) }

    val maxGridItems = remember(deviceClass) { when (deviceClass) { DeviceClass.LOW -> 8; DeviceClass.MID -> 15; DeviceClass.HIGH -> 30 } }
    val maxContinueItems = remember(deviceClass) { when (deviceClass) { DeviceClass.LOW -> 5; DeviceClass.MID -> 10; DeviceClass.HIGH -> 20 } }
    val maxBannerItems = remember(deviceClass) { when (deviceClass) { DeviceClass.LOW -> 3; DeviceClass.MID -> 5; DeviceClass.HIGH -> 8 } }

    val homeTrending = remember(mainState.homeTrending, maxGridItems) { mainState.homeTrending.take(maxGridItems) }
    val continueWatching = remember(mainState.continueWatching, maxContinueItems) { mainState.continueWatching.take(maxContinueItems) }
    val watchlist = remember(mainState.watchlist, maxContinueItems) { mainState.watchlist.take(maxContinueItems) }
    val bannerMovies = remember(heroState.bannerMovies, maxBannerItems) { heroState.bannerMovies.take(maxBannerItems) }
    
    val categoryMovies = remember(categoriesState.categoryMovies, maxGridItems) { categoriesState.categoryMovies.take(maxGridItems) }
    val categorySeries = remember(categoriesState.categorySeries, maxGridItems) { categoriesState.categorySeries.take(maxGridItems) }
    val categoryAnime = remember(categoriesState.categoryAnime, maxGridItems) { categoriesState.categoryAnime.take(maxGridItems) }
    val categoryCartoons = remember(categoriesState.categoryCartoons, maxGridItems) { categoriesState.categoryCartoons.take(maxGridItems) }
    val categoryCartoonSeries = remember(categoriesState.categoryCartoonSeries, maxGridItems) { categoriesState.categoryCartoonSeries.take(maxGridItems) }

    if (ua.ukrtv.app.BuildConfig.DEBUG) JankMonitor()

    LaunchedEffect(Unit) {
        viewModel.navigateToDetail.collect { movie -> onMovieClick(movie) }
    }
    LaunchedEffect(Unit) {
        viewModel.navigateToSearch.collect { query -> onSearchQueryClick(query) }
    }

    val context = LocalContext.current
    val onMovieFocused = remember { { movie: Movie -> viewModel.onMovieFocused(movie, context) } }
    val onDismiss = remember { { movie: Movie -> viewModel.dismissContinueWatching(movie) } }
    val top200ItemHandler = remember { { movie: Top200Movie -> viewModel.onTop200BannerClick(movie) } }

    val screenState = remember(
        mainState, categoriesState, heroState, configState, focusColor, activeBannerMovie,
        providerColor, bannerFocusRequester, bannerMovies
    ) {
        HomeScreenState(
            isLoading = mainState.isLoading,
            gridError = mainState.gridError,
            isOnline = configState.isOnline,
            top200Banners = heroState.top200Banners,
            bannerMovies = bannerMovies,
            continueWatching = continueWatching,
            watchlist = watchlist,
            homeTrending = homeTrending,
            trendingLabel = configState.trendingLabel,
            homeLayout = configState.homeLayout,
            categoryMovies = categoryMovies,
            categorySeries = categorySeries,
            categoryAnime = categoryAnime,
            categoryCartoons = categoryCartoons,
            categoryCartoonSeries = categoryCartoonSeries,
            activeBannerMovie = activeBannerMovie,
            providerColor = providerColor,
            focusColor = focusColor,
            bannerFocusRequester = bannerFocusRequester,
            providers = viewModel.providers,
            currentProviderId = configState.currentProviderId,
        )
    }

    HomeScreenContent(
        screenState = screenState,
        onRetryGrid = viewModel::retryGrid,
        onSearchClick = onSearchClick,
        onMovieClick = onMovieClick,
        onContinueWatchingClick = onContinueWatchingClick,
        onTop200ItemClick = top200ItemHandler,
        onTop200Click = onTop200Click,
        onMovieFocused = onMovieFocused,
        onActiveColorChange = { viewModel.provideFocusColor(it) },
        onDismissItem = onDismiss,
        onActiveMovieChange = { activeBannerMovie = it },
        onSeeAllTrendsClick = onSeeAllTrendsClick,
        onSeeAllCategoryClick = onSeeAllCategoryClick,
        onProviderClick = viewModel::switchProvider,
        onSettingsClick = onSettingsClick
    )
}

@Composable
private fun HomeScreenContent(
    screenState: HomeScreenState,
    onRetryGrid: () -> Unit,
    onSearchClick: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onContinueWatchingClick: (Movie) -> Unit,
    onTop200ItemClick: (Top200Movie) -> Unit,
    onTop200Click: () -> Unit,
    onMovieFocused: (Movie) -> Unit,
    onActiveColorChange: (Color) -> Unit,
    onDismissItem: (Movie) -> Unit,
    onActiveMovieChange: (Top200Movie) -> Unit,
    onSeeAllTrendsClick: () -> Unit,
    onSeeAllCategoryClick: (String) -> Unit = {},
    onProviderClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val gridState = rememberLazyListState()
    val density = LocalDensity.current
    val deviceClass = LocalDeviceClass.current

    val isLoading = screenState.isLoading
    val gridError = screenState.gridError
    val isOnline = screenState.isOnline
    val top200Banners = screenState.top200Banners
    val bannerMovies = screenState.bannerMovies
    val continueWatching = screenState.continueWatching
    val watchlist = screenState.watchlist
    val homeTrending = screenState.homeTrending
    val trendingLabel = screenState.trendingLabel
    val homeLayout = screenState.homeLayout
    val categoryMovies = screenState.categoryMovies
    val categorySeries = screenState.categorySeries
    val categoryAnime = screenState.categoryAnime
    val categoryCartoons = screenState.categoryCartoons
    val categoryCartoonSeries = screenState.categoryCartoonSeries
    val activeBannerMovie = screenState.activeBannerMovie
    val providerColor = screenState.providerColor
    val focusColor = screenState.focusColor
    val bannerFocusRequester = screenState.bannerFocusRequester
    val providers = screenState.providers
    val currentProviderId = screenState.currentProviderId

    val scrollFraction by remember {
        val hPx = with(density) { HeroDefaults.height.toPx() }
        derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) 1f
            else (gridState.firstVisibleItemScrollOffset / hPx).coerceIn(0f, 1f)
        }
    }
    val heroActive by remember { derivedStateOf { scrollFraction < 0.99f } }

    LaunchedEffect(gridState) {
        withContext(Dispatchers.Default) {
            snapshotFlow {
                if (gridState.isScrollInProgress) null
                else gridState.layoutInfo.visibleItemsInfo
            }.filterNotNull().debounce(800).collect { visibleItems ->
                val focusedItem = visibleItems.maxByOrNull { it.index }
                if (focusedItem != null) {
                    AppLogger.d("ScrollGate", "Settled at item ${focusedItem.index}")
                }
            }
        }
    }

    HandleBackNavigation(gridState, bannerFocusRequester, bannerMovies, top200Banners)
    AutoRestoreFocus(bannerFocusRequester, top200Banners, bannerMovies)

    val activeBannerAccent = remember(activeBannerMovie) {
        activeBannerMovie?.accentColor?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
        }
    }

    HomeBackground(
        focusedColor = focusColor,
        brandColor = providerColor,
        backdropColor = if (heroActive && activeBannerAccent != null) activeBannerAccent else Color.Unspecified,
        scrollFraction = { scrollFraction }
    ) {
        // Sync banner accent with background ambient
        LaunchedEffect(activeBannerAccent, heroActive) {
            if (heroActive && activeBannerAccent != null) {
                onActiveColorChange(activeBannerAccent)
            }
        }

        BannerBackdrop(activeBannerMovie, activeBannerAccent, heroActive && activeBannerMovie != null, { scrollFraction })

        Column {
            if (heroActive) {
                TopBar(
                    brandColor = providerColor,
                    providers = providers,
                    currentProviderId = currentProviderId,
                    scrollFraction = { scrollFraction },
                    onSearchClick = onSearchClick,
                    onProviderClick = onProviderClick,
                    onSettingsClick = onSettingsClick
                )
            }

            LazyColumn(
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = GridDefaults.horizontalPadding,
                    end = GridDefaults.horizontalPadding,
                    top = 8.dp,
                    bottom = GridDefaults.contentBottomPadding
                )
            ) {
                if (!isOnline) {
                    item(key = "offline_banner", contentType = "banner") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE53935).copy(alpha = 0.9f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "\u26A0 Немає підключення до інтернету",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (isLoading) {
                    items(6, key = { "shimmer_$it" }, contentType = { "shimmer" }) { ShimmerBox(Modifier.fillMaxWidth().height(280.dp).padding(bottom = 32.dp), Shapes.card) }
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
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = gridError,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onRetryGrid,
                                colors = ButtonDefaults.colors(
                                    containerColor = providerColor
                                )
                            ) {
                                Text("Повторити", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (top200Banners.isNotEmpty()) {
                    item(key = "banner_top200", contentType = "hero") {
                        Top200SignatureHero(
                            items = top200Banners,
                            brandColor = providerColor,
                            onItemClick = onTop200ItemClick,
                            onItemLongClick = { onTop200Click() },
                            onActiveMovieChange = onActiveMovieChange,
                            modifier = Modifier.focusRequester(bannerFocusRequester)
                        )
                    }
                } else if (bannerMovies.isNotEmpty()) {
                    item(key = "banner_hero", contentType = "hero") {
                        HeroCarousel(
                            items = bannerMovies,
                            brandColor = providerColor,
                            onWatchClick = onMovieClick,
                            onActiveColorChange = onActiveColorChange,
                            modifier = Modifier.focusRequester(bannerFocusRequester)
                        )
                    }
                }

                if (homeLayout.showContinueWatching && (continueWatching.isNotEmpty() || isLoading)) {
                    item(key = "continue_watching", contentType = "content_row") {
                        ContentRow("Продовжити перегляд", continueWatching, providerColor, onContinueWatchingClick, onDismissItem, onMovieFocused, useWideCards = true, isLoading = isLoading)
                    }
                }

                if (homeLayout.showWatchlist && (watchlist.isNotEmpty() || isLoading)) {
                    item(key = "watchlist", contentType = "content_row") {
                        ContentRow("Мій список", watchlist, providerColor, onMovieClick, null, onMovieFocused, isLoading = isLoading)
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
                            trailingContent = {
                                TrendsTrailingButton(
                                    brandColor = providerColor,
                                    onClick = onSeeAllTrendsClick,
                                    useLargeCards = true
                                )
                            }
                        )
                    }
                }

                if (homeLayout.showMovies && (categoryMovies.isNotEmpty() || isLoading)) {
                    item(key = "category_movies", contentType = "content_row") {
                        ContentRow(
                            title = "Фільми",
                            items = categoryMovies,
                            brandColor = providerColor,
                            onItemClick = onMovieClick,
                            onItemDismiss = null,
                            onItemFocused = onMovieFocused,
                            isLoading = isLoading,
                            trailingContent = {
                                TrendsTrailingButton(brandColor = providerColor, onClick = { onSeeAllCategoryClick("movies") })
                            }
                        )
                    }
                }

                if (homeLayout.showSeries && (categorySeries.isNotEmpty() || isLoading)) {
                    item(key = "category_series", contentType = "content_row") {
                        ContentRow(
                            title = "Серіали",
                            items = categorySeries,
                            brandColor = providerColor,
                            onItemClick = onMovieClick,
                            onItemDismiss = null,
                            onItemFocused = onMovieFocused,
                            isLoading = isLoading,
                            trailingContent = {
                                TrendsTrailingButton(brandColor = providerColor, onClick = { onSeeAllCategoryClick("series") })
                            }
                        )
                    }
                }

                if (homeLayout.showAnime && (categoryAnime.isNotEmpty() || isLoading)) {
                    item(key = "category_anime", contentType = "content_row") {
                        ContentRow(
                            title = "Аніме",
                            items = categoryAnime,
                            brandColor = providerColor,
                            onItemClick = onMovieClick,
                            onItemDismiss = null,
                            onItemFocused = onMovieFocused,
                            isLoading = isLoading,
                            trailingContent = {
                                TrendsTrailingButton(brandColor = providerColor, onClick = { onSeeAllCategoryClick("anime") })
                            }
                        )
                    }
                }

                if (homeLayout.showCartoons && (categoryCartoons.isNotEmpty() || isLoading)) {
                    item(key = "category_cartoons", contentType = "content_row") {
                        ContentRow(
                            title = "Мультфільми",
                            items = categoryCartoons,
                            brandColor = providerColor,
                            onItemClick = onMovieClick,
                            onItemDismiss = null,
                            onItemFocused = onMovieFocused,
                            isLoading = isLoading,
                            trailingContent = {
                                TrendsTrailingButton(brandColor = providerColor, onClick = { onSeeAllCategoryClick("cartoons") })
                            }
                        )
                    }
                }

                if (homeLayout.showCartoonSeries && (categoryCartoonSeries.isNotEmpty() || isLoading)) {
                    item(key = "category_cartoon_series", contentType = "content_row") {
                        ContentRow(
                            title = "Мультсеріали",
                            items = categoryCartoonSeries,
                            brandColor = providerColor,
                            onItemClick = onMovieClick,
                            onItemDismiss = null,
                            onItemFocused = onMovieFocused,
                            isLoading = isLoading,
                            trailingContent = {
                                TrendsTrailingButton(brandColor = providerColor, onClick = { onSeeAllCategoryClick("cartoon_series") })
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerBackdrop(movie: Top200Movie?, accent: Color?, visible: Boolean, scrollFraction: () -> Float = { 0f }) {
    val deviceClass = LocalDeviceClass.current
    val isMediatek = LocalIsMediatek.current
    val parallaxFactor = when (deviceClass) {
        DeviceClass.LOW -> 0f
        DeviceClass.MID -> 0.15f
        DeviceClass.HIGH -> 0.3f
    }
    if (visible && movie != null) {
        val backdropColor = accent ?: Color(0xFF08121c)

        if (deviceClass == DeviceClass.LOW) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(HeroDefaults.height + 80.dp)
                .graphicsLayer {
                    val h = size.height
                    val f = scrollFraction()
                    translationY = -f * h * parallaxFactor
                    alpha = (1f - f).coerceIn(0f, 1f)
                }
            ) {
                if (movie.backdropUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(movie.backdropUrl)
                            .size(960, 540)
                            .deviceImage(deviceClass, isMediatek)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(backdropColor, backdropColor.copy(alpha = 0.85f), Color.Transparent), endX = 1400f)
                ))
                Box(modifier = Modifier.fillMaxWidth().height(HeroDefaults.bottomFadeHeight).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(Color.Transparent, Background))
                ))
            }
        } else {
            val backdropHeight = if (deviceClass == DeviceClass.HIGH) 500.dp else 420.dp
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(backdropHeight)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithCache {
                    val brush = Brush.verticalGradient(
                        colors = listOf(Color.White, Color.Transparent),
                        startY = size.height * 0.45f,
                        endY = size.height
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = brush, blendMode = BlendMode.DstIn)
                    }
                }
                .graphicsLayer {
                    val h = size.height
                    val f = scrollFraction()
                    translationY = -f * h * parallaxFactor
                    alpha = (1f - f).coerceIn(0f, 1f)
                }
            ) {
                if (movie.backdropUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(movie.backdropUrl)
                            .size(1280, 720)
                            .deviceImage(deviceClass, isMediatek)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Multi-stage horizontal gradient for depth
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            backdropColor,
                            backdropColor.copy(alpha = 0.95f),
                            backdropColor.copy(alpha = 0.85f),
                            backdropColor.copy(alpha = 0.7f),
                            Color.Transparent
                        ),
                        endX = 1800f
                    )
                ))
            }
        }
    }
}

@Composable
private fun HandleBackNavigation(gridState: LazyListState, focusRequester: FocusRequester, bannerMovies: List<Movie>, top200Banners: List<Top200Movie>) {
    val coroutineScope = rememberCoroutineScope()
    val canGoBack by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.canScrollBackward } }
    BackHandler(enabled = canGoBack) {
        coroutineScope.launch {
            gridState.animateScrollToItem(0)
            if (top200Banners.isNotEmpty() || bannerMovies.isNotEmpty()) {
                focusRequester.requestFocus()
            }
        }
    }
}

@Composable
private fun JankMonitor() {
    val minJankMs = 50
    val lastFrameTime = remember { object { var value = 0L } }
    val callback = remember {
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (lastFrameTime.value != 0L) {
                    val durationMs = (frameTimeNanos - lastFrameTime.value) / 1_000_000
                    if (durationMs > minJankMs) {
                        AppLogger.w("Jank", "Frame ${durationMs}ms (dropped ${durationMs / 16 - 1})")
                    }
                }
                lastFrameTime.value = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }
    DisposableEffect(Unit) {
        Choreographer.getInstance().postFrameCallback(callback)
        onDispose { Choreographer.getInstance().removeFrameCallback(callback) }
    }
}

@Composable
private fun AutoRestoreFocus(focusRequester: FocusRequester, top200: List<Top200Movie>, banner: List<Movie>) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && (top200.isNotEmpty() || banner.isNotEmpty())) {
                scope.launch {
                    withFrameNanos { }
                    focusRequester.requestFocus()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

// ── PHONE HOME SCREEN ──

@Composable
private fun PhoneHomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onMovieClick: (Movie) -> Unit,
    onContinueWatchingClick: (Movie) -> Unit = onMovieClick,
    onSearchClick: () -> Unit,
    onSearchQueryClick: (String) -> Unit = { onSearchClick() },
    onTop200Click: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onSeeAllTrendsClick: () -> Unit = {},
    onSeeAllCategoryClick: (String) -> Unit = {},
) {
    val gridState = rememberLazyListState()
    val density = LocalDensity.current
    val mainState by viewModel.mainContentState.collectAsStateWithLifecycle()
    val categoriesState by viewModel.categoriesState.collectAsStateWithLifecycle()
    val heroState by viewModel.heroState.collectAsStateWithLifecycle()
    val configState by viewModel.configState.collectAsStateWithLifecycle()

    val providerColor = remember(configState.brandColor) { Color(configState.brandColor) }
    val providers = remember { viewModel.providers }
    var activeTop200Movie by remember { mutableStateOf<Top200Movie?>(null) }

    val maxItems = 12
    val continueWatching = remember(mainState.continueWatching) { mainState.continueWatching.take(maxItems) }
    val watchlist = remember(mainState.watchlist) { mainState.watchlist.take(maxItems) }
    val homeTrending = remember(mainState.homeTrending) { mainState.homeTrending.take(maxItems) }
    val categoryMovies = remember(categoriesState.categoryMovies) { categoriesState.categoryMovies.take(maxItems) }
    val categorySeries = remember(categoriesState.categorySeries) { categoriesState.categorySeries.take(maxItems) }
    val categoryAnime = remember(categoriesState.categoryAnime) { categoriesState.categoryAnime.take(maxItems) }
    val categoryCartoons = remember(categoriesState.categoryCartoons) { categoriesState.categoryCartoons.take(maxItems) }
    val categoryCartoonSeries = remember(categoriesState.categoryCartoonSeries) { categoriesState.categoryCartoonSeries.take(maxItems) }
    val scope = rememberCoroutineScope()

    val screenHeightDp = with(LocalDensity.current) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .background(Background)
                .statusBarsPadding()
        ) {
        // TopBar: UKRTV logo + Search + Settings
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
                Text("UKR", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("TV", color = providerColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1A1A1D), RoundedCornerShape(8.dp))
                        .clickable(onClick = onSearchClick)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Пошук...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                    }
                }
            }
        }

        // Sticky provider segmented control
        PhoneProviderSwitcher(
            providers = providers,
            currentProviderId = configState.currentProviderId,
            brandColor = providerColor,
            onProviderClick = { viewModel.switchProvider(it) }
        )

        val pullRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = mainState.isLoading,
            onRefresh = { viewModel.switchProvider(configState.currentProviderId) },
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
                        Text("\u26A0 Немає підключення", color = Color.White, fontSize = 13.sp)
                    }
                }
            }

            // Content rows
            if (configState.homeLayout.showContinueWatching && (continueWatching.isNotEmpty() || mainState.isLoading)) {
                item(key = "continue", contentType = "content_row") {
                    ContentRow("Продовжити перегляд", continueWatching, providerColor, onContinueWatchingClick, useWideCards = true, isLoading = mainState.isLoading)
                }
            }

            if (configState.homeLayout.showWatchlist && (watchlist.isNotEmpty() || mainState.isLoading)) {
                item(key = "watchlist", contentType = "content_row") {
                    ContentRow("Мій список", watchlist, providerColor, onMovieClick, isLoading = mainState.isLoading)
                }
            }

            if (configState.homeLayout.showTrends && (homeTrending.isNotEmpty() || mainState.isLoading)) {
                item(key = "trending", contentType = "content_row") {
                    ContentRow(
                        configState.trendingLabel,
                        homeTrending,
                        providerColor,
                        onMovieClick,
                        useLargeCards = true,
                        isLoading = mainState.isLoading,
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .width(PhoneCardDefaults.posterWidth)
                                    .height(PhoneCardDefaults.posterHeight)
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

            if (configState.homeLayout.showMovies && (categoryMovies.isNotEmpty() || mainState.isLoading)) {
                item(key = "cat_movies", contentType = "content_row") {
                    ContentRow(
                        "Фільми", categoryMovies, providerColor, onMovieClick,
                        isLoading = mainState.isLoading,
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .width(PhoneCardDefaults.posterWidth)
                                    .height(PhoneCardDefaults.posterHeight)
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

            if (configState.homeLayout.showSeries && (categorySeries.isNotEmpty() || mainState.isLoading)) {
                item(key = "cat_series", contentType = "content_row") {
                    ContentRow(
                        "Серіали", categorySeries, providerColor, onMovieClick,
                        isLoading = mainState.isLoading,
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .width(PhoneCardDefaults.posterWidth)
                                    .height(PhoneCardDefaults.posterHeight)
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

            if (configState.homeLayout.showAnime && (categoryAnime.isNotEmpty() || mainState.isLoading)) {
                item(key = "cat_anime", contentType = "content_row") {
                    ContentRow(
                        "Аніме", categoryAnime, providerColor, onMovieClick,
                        isLoading = mainState.isLoading,
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .width(PhoneCardDefaults.posterWidth)
                                    .height(PhoneCardDefaults.posterHeight)
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

            if (configState.homeLayout.showCartoons && (categoryCartoons.isNotEmpty() || mainState.isLoading)) {
                item(key = "cat_cartoons", contentType = "content_row") {
                    ContentRow(
                        "Мультфільми", categoryCartoons, providerColor, onMovieClick,
                        isLoading = mainState.isLoading,
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .width(PhoneCardDefaults.posterWidth)
                                    .height(PhoneCardDefaults.posterHeight)
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

            if (configState.homeLayout.showCartoonSeries && (categoryCartoonSeries.isNotEmpty() || mainState.isLoading)) {
                item(key = "cat_cartoon_series", contentType = "content_row") {
                    ContentRow(
                        "Мультсеріали", categoryCartoonSeries, providerColor, onMovieClick,
                        isLoading = mainState.isLoading,
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .width(PhoneCardDefaults.posterWidth)
                                    .height(PhoneCardDefaults.posterHeight)
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

            if (!mainState.isLoading && continueWatching.isEmpty() && watchlist.isEmpty() && homeTrending.isEmpty()) {
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
}
}
