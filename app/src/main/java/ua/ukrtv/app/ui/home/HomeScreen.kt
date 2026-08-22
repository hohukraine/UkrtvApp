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
import androidx.compose.ui.draw.clipToBounds
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
import ua.ukrtv.app.ui.home.components.HomeSectionUi
import ua.ukrtv.app.ui.home.components.MainSectionRow
import ua.ukrtv.app.ui.home.components.YouTvFocusInfoPanel
import ua.ukrtv.app.ui.home.components.YouTvMovieHero
import ua.ukrtv.app.ui.home.components.YouTvTop200Hero
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
    sharedTransitionScope: SharedTransitionScope?,
    animatedContentScope: AnimatedContentScope?,
    onMovieClick: (Movie) -> Unit,
    onContinueWatchingClick: (Movie) -> Unit,
    onSearchClick: () -> Unit,
    onSearchQueryClick: (String) -> Unit,
    onTop200Click: () -> Unit,
    onTop200ItemClick: (Top200Movie) -> Unit,
    onSeeAllTrendsClick: () -> Unit,
    onSeeAllCategoryClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onHomeContentReady: () -> Unit,
    onUpdateClick: () -> Unit
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

    // YouTV-style home: fixed hero on top, a single section row below that swaps content on ↑/↓.
    val sections = remember(mainState, categoriesState, configState, maxItems) {
        buildList {
            if (configState.homeLayout.showContinueWatching && (mainState.continueWatching.isNotEmpty() || mainState.isLoading)) {
                add(
                    HomeSectionUi(
                        id = "continue_watching",
                        title = "Продовжити перегляд",
                        items = mainState.continueWatching.take(maxItems),
                        isLoading = mainState.isLoading,
                        dismissable = true
                    )
                )
            }
            if (configState.homeLayout.showWatchlist && (mainState.watchlist.isNotEmpty() || mainState.isLoading)) {
                add(
                    HomeSectionUi(
                        id = "watchlist",
                        title = "Мій список",
                        items = mainState.watchlist.take(maxItems),
                        isLoading = mainState.isLoading
                    )
                )
            }
            if (configState.homeLayout.showTrends && (mainState.homeTrending.isNotEmpty() || mainState.isLoading)) {
                add(
                    HomeSectionUi(
                        id = "trending",
                        title = configState.trendingLabel,
                        items = mainState.homeTrending,
                        isLoading = mainState.isLoading,
                        useLargeCards = true
                    )
                )
            }
            if (configState.homeLayout.showMovies && (categoriesState.categoryMovies.isNotEmpty() || categoriesState.isLoading)) {
                add(
                    HomeSectionUi(
                        id = "category_movies",
                        title = "Фільми",
                        items = categoriesState.categoryMovies.take(maxItems),
                        isLoading = categoriesState.isLoading,
                        categoryKey = "movies"
                    )
                )
            }
            if (configState.homeLayout.showSeries && (categoriesState.categorySeries.isNotEmpty() || categoriesState.isLoading)) {
                add(
                    HomeSectionUi(
                        id = "category_series",
                        title = "Серіали",
                        items = categoriesState.categorySeries.take(maxItems),
                        isLoading = categoriesState.isLoading,
                        categoryKey = "series"
                    )
                )
            }
            if (configState.homeLayout.showAnime && (categoriesState.categoryAnime.isNotEmpty() || categoriesState.isLoading)) {
                add(
                    HomeSectionUi(
                        id = "category_anime",
                        title = "Аніме",
                        items = categoriesState.categoryAnime.take(maxItems),
                        isLoading = categoriesState.isLoading,
                        categoryKey = "anime"
                    )
                )
            }
            if (configState.homeLayout.showCartoons && (categoriesState.categoryCartoons.isNotEmpty() || categoriesState.isLoading)) {
                add(
                    HomeSectionUi(
                        id = "category_cartoons",
                        title = "Мультфільми",
                        items = categoriesState.categoryCartoons.take(maxItems),
                        isLoading = categoriesState.isLoading,
                        categoryKey = "cartoons"
                    )
                )
            }
            if (configState.homeLayout.showCartoonSeries && (categoriesState.categoryCartoonSeries.isNotEmpty() || categoriesState.isLoading)) {
                add(
                    HomeSectionUi(
                        id = "category_cartoon_series",
                        title = "Мультсеріали",
                        items = categoriesState.categoryCartoonSeries.take(maxItems),
                        isLoading = categoriesState.isLoading,
                        categoryKey = "cartoon_series"
                    )
                )
            }
        }
    }

    // Focus restore after returning from another screen: preselect the section holding it.
    val restoreTarget = remember { focusedMovie }
    var restoreWindowOpen by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(FOCUS_RESTORE_WINDOW_MS)
        restoreWindowOpen = false
    }

    var activeIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(sections) {
        if (sections.isEmpty()) return@LaunchedEffect
        if (activeIndex < 0 || activeIndex > sections.lastIndex) {
            val target = restoreTarget?.let { t ->
                sections.indexOfFirst { s -> s.items.any { m -> m.pageUrl == t.pageUrl } }
            } ?: -1
            activeIndex = if (target >= 0) target else 0
        }
    }

    var rowHasFocus by remember { mutableStateOf(false) }
    // YouTV transition: once a section row takes focus the banner fades away entirely and
    // the background becomes the focused poster's full-bleed backdrop.
    val heroAlpha by animateFloatAsState(
        targetValue = if (rowHasFocus) 0f else 1f,
        animationSpec = tween(250),
        label = "heroFade"
    )
    val heroBackdropColor = activeBannerMovie?.accentColor?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { Color.Unspecified }
    } ?: Color.Unspecified

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        HomeBackground(
            focusedColor = focusColor,
            brandColor = providerColor,
            backdropColor = if (rowHasFocus) focusColor else heroBackdropColor,
            backdropUrl = if (rowHasFocus) focusedMovie?.poster else activeBannerMovie?.backdropUrl,
            backdropBlur = if (rowHasFocus && deviceClass == DeviceClass.HIGH) 28.dp else 0.dp,
            immersive = !rowHasFocus,
            scrollFraction = { 0f }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    currentProviderId = configState.currentProviderId,
                    brandColor = providerColor,
                    onSearchClick = onSearchClick,
                    onSettingsClick = onSettingsClick
                )

                if (!configState.isOnline) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = GridDefaults.horizontalPadding, vertical = 16.dp)
                            .background(Color(0xFFE53935).copy(alpha = 0.8f), Shapes.card)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TvText(
                            "Відсутнє підключення до мережі. Відображення кешованих даних.",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                        .padding(top = 12.dp)
                ) {
                    if (heroState.top200Banners.isNotEmpty()) {
                        YouTvTop200Hero(
                            items = heroState.top200Banners,
                            brandColor = providerColor,
                            onItemClick = onTop200ItemClick,
                            onActiveMovieChange = { activeBannerMovie = it },
                            modifier = Modifier
                                .focusRequester(bannerFocusRequester)
                                .graphicsLayer { alpha = heroAlpha }
                        )
                    } else if (heroState.bannerMovies.isNotEmpty()) {
                        YouTvMovieHero(
                            items = heroState.bannerMovies,
                            brandColor = providerColor,
                            onWatchClick = onMovieClick,
                            onActiveColorChange = { viewModel.provideFocusColor(Color(it)) },
                            modifier = Modifier
                                .focusRequester(bannerFocusRequester)
                                .graphicsLayer { alpha = heroAlpha }
                        )
                    }

                    // YouTV keeps its info widget visible while a row is focused — it just
                    // repoints at the focused card (title/pill/meta/description, no button).
                    // Fallback to the section's first item so the spot is never empty.
                    val infoPanelMovie = focusedMovie
                        ?: sections.getOrNull(activeIndex.coerceIn(0, sections.lastIndex))?.items?.firstOrNull()
                    infoPanelMovie?.let { movie ->
                        YouTvFocusInfoPanel(
                            movie = movie,
                            brandColor = providerColor,
                            modifier = Modifier.graphicsLayer { alpha = 1f - heroAlpha }
                        )
                    }
                }

                if (!mainState.isLoading && mainState.gridError != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = GridDefaults.horizontalPadding, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvText(
                            "Помилка завантаження:\n${mainState.gridError}",
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        TvButton(
                            onClick = { viewModel.retryGrid() },
                            colors = TvButtonDefaults.colors(
                                containerColor = providerColor,
                                contentColor = Color.White
                            )
                        ) {
                            TvText("Спробувати знову", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (sections.isNotEmpty() && activeIndex >= 0) {
                    MainSectionRow(
                        sections = sections,
                        activeIndex = activeIndex,
                        onSectionChange = { activeIndex = it },
                        brandColor = providerColor,
                        providerHint = configState.currentProviderId,
                        onMovieClick = { movie ->
                            if (movie.watchProgress != null) onContinueWatchingClick(movie) else onMovieClick(movie)
                        },
                        onItemDismiss = { viewModel.dismissContinueWatching(it) },
                        onItemFocused = { viewModel.onMovieFocused(it, context) },
                        onSeeAllClick = { section ->
                            when {
                                section.id == "trending" -> onSeeAllTrendsClick()
                                section.id == "watchlist" -> onSeeAllCategoryClick("watchlist")
                                section.categoryKey != null -> onSeeAllCategoryClick(section.categoryKey)
                            }
                        },
                        restoreMovie = restoreTarget,
                        restoreWindowOpen = { restoreWindowOpen },
                        onRestoreHandled = { restoreWindowOpen = false },
                        onRowFocusChange = { rowHasFocus = it },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope
                    )
                }
            }
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
