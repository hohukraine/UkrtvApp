package ua.ukrtv.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.view.Choreographer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.ui.components.ShimmerBox
import ua.ukrtv.app.ui.home.components.ContentRow
import ua.ukrtv.app.ui.home.components.HeroCarousel
import ua.ukrtv.app.ui.home.components.Top200SignatureHero
import ua.ukrtv.app.ui.home.components.TopBar
import ua.ukrtv.app.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onMovieClick: (Movie) -> Unit,
    onContinueWatchingClick: (Movie) -> Unit = onMovieClick,
    onSearchClick: () -> Unit,
    onSearchQueryClick: (String) -> Unit = { onSearchClick() },
    onTop200Click: () -> Unit,
    onTop200ItemClick: (Top200Movie) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val grid by viewModel.grid.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val bannerMovies by viewModel.banner.collectAsState()
    val top200Banners by viewModel.top200.collectAsState()

    val brandColorLong by viewModel.brandColor.collectAsState()
    val currentProviderId by viewModel.currentProviderId.collectAsState()
    val focusedMovie by viewModel.focusedMovie.collectAsState()
    val focusColor by viewModel.focusColor.collectAsState()

    val bannerFocusRequester = remember { FocusRequester() }
    var activeBannerMovie by remember { mutableStateOf<Top200Movie?>(null) }
    val gridState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val providerColor = remember(brandColorLong) { Color(brandColorLong) }
    val activeBannerAccent = remember(activeBannerMovie?.accentColor) {
        activeBannerMovie?.accentColor?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
        }
    }

    val isScrolled by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 80 } }

    val ambientColor by animateColorAsState(
        targetValue = when {
            focusedMovie != null -> focusColor
            !isScrolled && activeBannerAccent != null -> activeBannerAccent
            else -> providerColor
        },
        animationSpec = tween(500),
        label = "ambientColor"
    )

    JankMonitor()
    HandleBackNavigation(gridState, bannerFocusRequester)
    AutoRestoreFocus(bannerFocusRequester, top200Banners, bannerMovies)

    LaunchedEffect(Unit) {
        viewModel.navigateToDetail.collect { movie -> onMovieClick(movie) }
    }
    LaunchedEffect(Unit) {
        viewModel.navigateToSearch.collect { query -> onSearchQueryClick(query) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawAmbientBackground(ambientColor, providerColor)
    ) {
        BannerBackdrop(activeBannerMovie, activeBannerAccent, isScrolled)

        Column {
            TopBarSection(isScrolled, providerColor, viewModel, currentProviderId, onSearchClick)

            val context = LocalContext.current
            val top200ItemHandler = { movie: Top200Movie -> viewModel.onTop200BannerClick(movie) }
            MainContentRows(
                gridState = gridState,
                isLoading = bannerMovies.isEmpty() && grid.isEmpty() && top200Banners.isEmpty(),
                top200Banners = top200Banners,
                bannerMovies = bannerMovies,
                continueWatching = continueWatching,
                watchlist = watchlist,
                grid = grid,
                providerColor = providerColor,
                bannerFocusRequester = bannerFocusRequester,
                onMovieClick = onMovieClick,
                onContinueWatchingClick = onContinueWatchingClick,
                onTop200ItemClick = top200ItemHandler,
                onTop200Click = onTop200Click,
                onMovieFocused = { viewModel.onMovieFocused(it, context) },
                onDismissItem = { viewModel.dismissContinueWatching(it) },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onActiveMovieChange = { activeBannerMovie = it }
            )
        }
    }
}

@Composable
private fun Modifier.drawAmbientBackground(ambientColor: Color, providerColor: Color): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                ambientColor.copy(alpha = 0.12f),
                ambientColor.copy(alpha = 0.02f),
                providerColor.copy(alpha = 0.06f),
                providerColor.copy(alpha = 0.02f)
            )
        )
    )
}

@Composable
private fun BannerBackdrop(movie: Top200Movie?, accent: Color?, isScrolled: Boolean) {
    AnimatedVisibility(
        visible = !isScrolled && movie != null,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(400))
    ) {
        movie?.let { 
            val backdropColor = accent ?: Color(0xFF08121c)
            Box(modifier = Modifier.fillMaxWidth().height(HeroDefaults.height + 80.dp)) {
                if (it.backdropUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(it.backdropUrl)
                            .crossfade(false)
                            .size(1920, 1080)
                            .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(backdropColor, backdropColor.copy(0.9f), backdropColor.copy(0.84f)), endX = 1600f)
                ))
                Box(modifier = Modifier.fillMaxWidth().height(HeroDefaults.bottomFadeHeight).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(Color.Transparent, Background))
                ))
            }
        }
    }
}

@Composable
private fun TopBarSection(isScrolled: Boolean, color: Color, viewModel: HomeViewModel, providerId: String, onSearchClick: () -> Unit) {
    AnimatedVisibility(
        visible = !isScrolled,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)),
        exit = fadeOut(tween(300)) + slideOutVertically(tween(300))
    ) {
        TopBar(
            brandColor = color,
            providers = viewModel.providers,
            currentProviderId = providerId,
            onSearchClick = onSearchClick,
            onProviderClick = viewModel::switchProvider
        )
    }
}

@Composable
private fun MainContentRows(
    gridState: LazyListState,
    isLoading: Boolean,
    top200Banners: List<Top200Movie>,
    bannerMovies: List<Movie>,
    continueWatching: List<Movie>,
    watchlist: List<Movie>,
    grid: List<Movie>,
    providerColor: Color,
    bannerFocusRequester: FocusRequester,
    onMovieClick: (Movie) -> Unit,
    onContinueWatchingClick: (Movie) -> Unit,
    onTop200ItemClick: (Top200Movie) -> Unit,
    onTop200Click: () -> Unit,
    onMovieFocused: (Movie) -> Unit,
    onDismissItem: (Movie) -> Unit,
    onActiveMovieChange: (Top200Movie) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
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
        if (isLoading) {
            items(6, key = { "shimmer_$it" }) { ShimmerBox(Modifier.fillMaxWidth().height(280.dp).padding(bottom = 32.dp), Shapes.card) }
        }

        if (top200Banners.isNotEmpty()) {
            item(key = "banner_top200") {
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
            item(key = "banner_hero") {
                HeroCarousel(
                    items = bannerMovies,
                    brandColor = providerColor,
                    onWatchClick = onMovieClick,
                    modifier = Modifier.focusRequester(bannerFocusRequester)
                )
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "continue_watching") {
                ContentRow("Продовжити перегляд", continueWatching, providerColor, onContinueWatchingClick, onDismissItem, onMovieFocused, useWideCards = true, sharedTransitionScope = sharedTransitionScope, animatedVisibilityScope = animatedVisibilityScope)
            }
        }

        if (watchlist.isNotEmpty()) {
            item(key = "watchlist") {
                ContentRow("Мій список", watchlist, providerColor, onMovieClick, null, onMovieFocused, sharedTransitionScope = sharedTransitionScope, animatedVisibilityScope = animatedVisibilityScope)
            }
        }

        if (grid.isNotEmpty()) {
            item(key = "trending") {
                ContentRow("Тренди", grid, providerColor, onMovieClick, null, onMovieFocused, useLargeCards = true, sharedTransitionScope = sharedTransitionScope, animatedVisibilityScope = animatedVisibilityScope)
            }
        }
    }
}

@Composable
private fun HandleBackNavigation(gridState: LazyListState, focusRequester: FocusRequester) {
    val coroutineScope = rememberCoroutineScope()
    val canGoBack by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    BackHandler(enabled = canGoBack) {
        coroutineScope.launch {
            gridState.animateScrollToItem(0)
            focusRequester.requestFocus()
        }
    }
}

@Composable
private fun JankMonitor() {
    val minJankMs = 20
    var lastFrameTime by remember { mutableLongStateOf(0L) }
    val callback = remember {
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (lastFrameTime != 0L) {
                    val durationMs = (frameTimeNanos - lastFrameTime) / 1_000_000
                    if (durationMs > minJankMs) {
                        AppLogger.w("Jank", "Frame ${durationMs}ms (dropped ${durationMs / 16 - 1})")
                    }
                }
                lastFrameTime = frameTimeNanos
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
    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    if (top200.isNotEmpty() || banner.isNotEmpty()) focusRequester.requestFocus()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
