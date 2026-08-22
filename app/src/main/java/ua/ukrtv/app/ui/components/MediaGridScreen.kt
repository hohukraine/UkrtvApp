package ua.ukrtv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.home.components.HomeBackground
import ua.ukrtv.app.ui.theme.*
import ua.ukrtv.app.util.DeviceClass

@Composable
fun MediaGridScreen(
    title: String,
    items: List<Movie>,
    isLoading: Boolean,
    error: String?,
    brandColor: Color,
    onMovieClick: (Movie) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    isLoadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null
) {
    val formFactor = LocalFormFactor.current
    if (formFactor == FormFactor.TV) {
        TvMediaGridScreen(title, items, isLoading, error, brandColor, onMovieClick, onBack, onRetry, isLoadingMore, onLoadMore)
    } else {
        PhoneMediaGridScreen(title, items, isLoading, error, brandColor, onMovieClick, onBack, onRetry)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvMediaGridScreen(
    title: String,
    items: List<Movie>,
    isLoading: Boolean,
    error: String?,
    brandColor: Color,
    onMovieClick: (Movie) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    isLoadingMore: Boolean,
    onLoadMore: (() -> Unit)?
) {
    val deviceClass = LocalDeviceClass.current
    val gridFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var focusedMovie by remember { mutableStateOf<Movie?>(null) }

    // Lazy pagination: fetch more when the focus/viewport approaches the end of loaded items.
    LaunchedEffect(items.size, onLoadMore) {
        if (onLoadMore == null) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (items.isNotEmpty() && lastVisible >= items.size - 12) onLoadMore()
            }
    }

    var entranceTrigger by remember { mutableStateOf(0L) }
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            entranceTrigger = System.currentTimeMillis()
            withFrameNanos { }
            gridFocusRequester.requestFocus()
        }
    }

    val scrollFraction by remember {
        derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) 1f
            else (gridState.firstVisibleItemScrollOffset / 200f).coerceIn(0f, 1f)
        }
    }

    HomeBackground(
        brandColor = brandColor,
        focusedColor = brandColor,
        scrollFraction = { scrollFraction },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    shape = ClickableSurfaceDefaults.shape(if (deviceClass == DeviceClass.HIGH) CircleShape else RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.05f),
                        focusedContainerColor = Color.White,
                        contentColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = if (deviceClass == DeviceClass.HIGH) 26.sp else 24.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!isLoading) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "(${items.size})",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 16.sp
                    )
                }
            }

            // YouTV-style info line: shows the currently focused grid item.
            GridFocusInfoPanel(movie = focusedMovie, brandColor = brandColor)

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(gridFocusRequester)
            ) {
                if (!isLoading && error != null && items.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = error, color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.tv.material3.Button(onClick = onRetry) { Text("Повторити") }
                        }
                    }
                }
                itemsIndexed(items, key = { _, movie -> movie.id }, contentType = { _, _ -> "movie" }) { index, movie ->
                    CompactMediaCard(
                        movie,
                        { onMovieClick(movie) },
                        index,
                        entranceTrigger,
                        deviceClass,
                        gridState,
                        onFocused = { focusedMovie = it }
                    )
                }
                if (isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "loading_more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = brandColor, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CompactMediaCard(
    movie: Movie,
    onClick: () -> Unit,
    entranceIndex: Int,
    entranceTrigger: Long,
    deviceClass: DeviceClass,
    gridState: LazyGridState,
    onFocused: ((Movie) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isMediatek = LocalIsMediatek.current

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused?.invoke(movie)
    }

    var itemVisible by remember(entranceTrigger, entranceIndex) { mutableStateOf(deviceClass == DeviceClass.LOW) }
    LaunchedEffect(entranceTrigger, entranceIndex) {
        if (deviceClass != DeviceClass.LOW) {
            val isInitialVisible = entranceIndex <= gridState.layoutInfo.visibleItemsInfo.size
            if (!isInitialVisible) { itemVisible = true; return@LaunchedEffect }
            itemVisible = true
        }
    }
    
    val entranceAlpha by animateFloatAsState(if (itemVisible) 1f else 0f, tween(200))
    val entranceScale by animateFloatAsState(if (itemVisible) 1f else 0.95f, spring(0.7f, 300f))
    val focusScale = if (isFocused) 1.05f else 1f

    val (gridW, gridH) = when (deviceClass) {
        DeviceClass.LOW -> 180 to 270
        DeviceClass.MID -> 300 to 450
        else -> 360 to 540
    }
    
    Column(modifier = Modifier.graphicsLayer {
        val s = entranceScale * focusScale
        scaleX = s; scaleY = s; alpha = entranceAlpha
    }) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            modifier = Modifier.aspectRatio(2f/3f).fillMaxWidth()
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(movie.poster).size(gridW, gridH).deviceImage(deviceClass, isMediatek).build(),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    placeholder = PlaceholderDark
                )
                movie.provider?.let { p ->
                    val pColor = if (p == "Uakino") Color(0xFFFF6B35) else Color(0xFF4ECDC4)
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(pColor.copy(0.85f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text(p.uppercase(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(movie.title, color = Color.White.copy(0.8f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun GridFocusInfoPanel(movie: Movie?, brandColor: Color) {
    // Fixed height so the grid does not jump when focus info appears/disappears.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        if (movie != null) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = movie.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val meta = buildList {
                        movie.year?.let { add(it.toString()) }
                        movie.contentType?.let { add(it) }
                        movie.rating?.let { add("★ $it") }
                    }.joinToString("  •  ")
                    if (meta.isNotEmpty()) {
                        Spacer(Modifier.width(10.dp))
                        Text(text = meta, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1)
                    }
                }
                movie.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = desc,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneMediaGridScreen(
    title: String,
    items: List<Movie>,
    isLoading: Boolean,
    error: String?,
    brandColor: Color,
    onMovieClick: (Movie) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (!isLoading) Text(" (${items.size})", color = Color.White.copy(0.5f), fontSize = 14.sp)
        }
        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = brandColor) }
        } else if (error != null && items.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = Color.White.copy(0.6f))
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Button(onClick = onRetry) { Text("Повторити") }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(PhoneGridDefaults.columns),
                horizontalArrangement = Arrangement.spacedBy(PhoneGridDefaults.columnSpacing),
                verticalArrangement = Arrangement.spacedBy(PhoneGridDefaults.rowSpacing),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(items, key = { it.id }) { movie ->
                    Column(Modifier.fillMaxWidth().clickable { onMovieClick(movie) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f/3f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF141414))) {
                            AsyncImage(
                                model = movie.poster,
                                contentDescription = movie.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(movie.title, color = Color.White.copy(0.8f), fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
