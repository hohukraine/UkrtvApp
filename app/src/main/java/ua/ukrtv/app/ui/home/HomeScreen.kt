package ua.ukrtv.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import kotlinx.coroutines.launch
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.ui.home.components.TopBar
import ua.ukrtv.app.ui.home.components.ContentRow
import ua.ukrtv.app.ui.home.components.HeroCarousel
import ua.ukrtv.app.ui.home.components.Top200SignatureHero
import ua.ukrtv.app.ui.home.FocusInfoPanel
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.GridDefaults
import ua.ukrtv.app.ui.theme.OnSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onMovieClick: (Movie) -> Unit,
    onSearchClick: () -> Unit,
    onTop200Click: () -> Unit,
    onTop200ItemClick: (Top200Movie) -> Unit
) {
    val grid by viewModel.gridState.collectAsState()
    val continueWatching by viewModel.continueWatchingState.collectAsState()
    val watchlist by viewModel.watchlistState.collectAsState()
    val bannerMovies by viewModel.bannerState.collectAsState()
    val top200Banners by viewModel.top200Banners.collectAsState()
    val brandColorLong by viewModel.brandColor.collectAsState()
    val currentProviderId by viewModel.currentProviderId.collectAsState()
    val focusedMovie by viewModel.focusedMovie.collectAsState()

    val providers = viewModel.providers
    val onDismissItem: (Movie) -> Unit = { viewModel.dismissContinueWatching(it) }
    val searchFocusRequester = remember { FocusRequester() }
    val bannerFocusRequester = remember { FocusRequester() }

    val hasBanner = top200Banners.isNotEmpty() || bannerMovies.isNotEmpty()
    val hasContinueWatching = continueWatching.isNotEmpty()
    val hasWatchlist = watchlist.isNotEmpty()

    LaunchedEffect(top200Banners, bannerMovies) {
        withFrameNanos {
            if (top200Banners.isNotEmpty()) {
                bannerFocusRequester.requestFocus()
            } else if (bannerMovies.isNotEmpty()) {
                bannerFocusRequester.requestFocus()
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var focusedGridIndex by remember { mutableIntStateOf(-1) }

    val isNotAtTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    BackHandler(enabled = isNotAtTop) {
        coroutineScope.launch {
            gridState.animateScrollToItem(0)
            withFrameNanos { bannerFocusRequester.requestFocus() }
        }
    }

    val mainGridOffset = remember(hasBanner, hasContinueWatching, hasWatchlist) {
        var offset = 0
        if (hasBanner) offset += 1
        if (hasContinueWatching) offset += 2
        if (hasWatchlist) offset += 2
        offset
    }

    val color by animateColorAsState(
        targetValue = Color(brandColorLong),
        animationSpec = tween(400),
        label = "brandColor"
    )

    // Critical fix: Ensure InfoPanel only shows when a card in the grid is focused
    val showInfoPanel by remember(focusedMovie, focusedGridIndex) {
        derivedStateOf { focusedMovie != null && focusedGridIndex != -1 }
    }

    val bgGradient = remember(color) {
        Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = 0.15f),
                Background
            ),
            startY = 0f,
            endY = 600f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .background(bgGradient)
    ) {
        Column {
            TopBar(
                brandColor = color,
                providers = providers,
                currentProviderId = currentProviderId,
                onSearchClick = onSearchClick,
                onProviderClick = viewModel::switchProvider,
                searchFocusRequester = searchFocusRequester
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(GridDefaults.columns),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.DirectionUp) {
                            val visibleItems = gridState.layoutInfo.visibleItemsInfo
                            if (visibleItems.isNotEmpty()) {
                                val firstY = visibleItems.first().offset.y
                                val firstRowIndices = visibleItems
                                    .takeWhile { it.offset.y == firstY }
                                    .map { it.index }
                                    .toSet()
                                if (gridState.firstVisibleItemIndex > 0 && focusedGridIndex in firstRowIndices) {
                                    coroutineScope.launch {
                                        gridState.animateScrollToItem(0)
                                        withFrameNanos { bannerFocusRequester.requestFocus() }
                                    }
                                    return@onKeyEvent true
                                }
                            }
                            false
                        } else false
                    },
                contentPadding = PaddingValues(
                    start = GridDefaults.horizontalPadding,
                    end = GridDefaults.horizontalPadding,
                    top = 8.dp,
                    bottom = GridDefaults.contentBottomPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(GridDefaults.columnSpacing),
                verticalArrangement = Arrangement.spacedBy(GridDefaults.rowSpacing)
            ) {
                if (top200Banners.isNotEmpty()) {
                    item(key = "banner_top200", span = { GridItemSpan(GridDefaults.columns) }) {
                        Top200SignatureHero(
                            items = top200Banners,
                            brandColor = color,
                            onItemClick = { movie -> onTop200ItemClick(movie) },
                            onItemLongClick = { onTop200Click() },
                            modifier = Modifier.focusRequester(bannerFocusRequester)
                        )
                    }
                } else if (bannerMovies.isNotEmpty()) {
                    item(key = "banner_hero", span = { GridItemSpan(GridDefaults.columns) }) {
                        HeroCarousel(
                            items = bannerMovies,
                            brandColor = color,
                            onWatchClick = onMovieClick,
                            modifier = Modifier.focusRequester(bannerFocusRequester)
                        )
                    }
                }

                if (continueWatching.isNotEmpty()) {
                    item(key = "cw_header", span = { GridItemSpan(GridDefaults.columns) }) {
                        Text(
                            text = "Continue Watching",
                            color = OnSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, start = GridDefaults.horizontalPadding)
                        )
                    }
                    item(key = "continue_watching", span = { GridItemSpan(GridDefaults.columns) }) {
                        ContentRow(
                            title = "",
                            items = continueWatching,
                            brandColor = color,
                            onItemClick = onMovieClick,
                            onItemDismiss = onDismissItem,
                            useWideCards = true
                        )
                    }
                }

                if (watchlist.isNotEmpty()) {
                    item(key = "wl_header", span = { GridItemSpan(GridDefaults.columns) }) {
                        Text(
                            text = "My List",
                            color = OnSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, start = GridDefaults.horizontalPadding)
                        )
                    }
                    item(key = "watchlist", span = { GridItemSpan(GridDefaults.columns) }) {
                        ContentRow(
                            title = "",
                            items = watchlist,
                            brandColor = color,
                            onItemClick = onMovieClick,
                            onItemDismiss = null,
                            useWideCards = false
                        )
                    }
                }

                item(key = "trending_header", span = { GridItemSpan(GridDefaults.columns) }) {
                    Text(
                        text = "Trending Now",
                        color = OnSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, start = GridDefaults.horizontalPadding)
                    )
                }

                itemsIndexed(
                    items = grid,
                    key = { _, item -> item.pageUrl },
                    contentType = { _, _ -> "movie" }
                ) { index, movie ->
                    val gridIndex = mainGridOffset + index
                    Box(
                        modifier = Modifier
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    focusedGridIndex = gridIndex
                                    viewModel.onMovieFocused(movie)
                                } else if (!state.isFocused && focusedGridIndex == gridIndex) {
                                    focusedGridIndex = -1
                                }
                            }
                    ) {
                        MovieCard(
                            movie = movie,
                            brandColor = color,
                            width = CardDefaults.posterWidth,
                            height = CardDefaults.posterHeight,
                            isExpanded = focusedGridIndex == gridIndex,
                            onClick = { onMovieClick(movie) }
                        )
                    }
                }
            }

        }

        if (showInfoPanel) {
            FocusInfoPanel(
                movie = focusedMovie!!,
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                brandColor = color
            )
        }
    }
}
