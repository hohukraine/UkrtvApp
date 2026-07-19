package ua.ukrtv.app.ui.category

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import ua.ukrtv.app.ui.theme.PlaceholderDark
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.home.components.HomeBackground
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalFormFactor
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.ui.theme.PhoneCardDefaults
import ua.ukrtv.app.ui.theme.PhoneGridDefaults
import ua.ukrtv.app.ui.theme.SurfaceVariant
import ua.ukrtv.app.ui.theme.deviceImage
import ua.ukrtv.app.util.DeviceClass

@Composable
fun FullCategoryGridScreen(
    viewModel: FullCategoryGridViewModel = hiltViewModel(),
    onMovieClick: (Movie) -> Unit,
    onBack: () -> Unit
) {
    val formFactor = LocalFormFactor.current
    when (formFactor) {
        FormFactor.TV -> TvFullCategoryGridScreen(viewModel, onMovieClick, onBack)
        FormFactor.PHONE, FormFactor.TABLET -> PhoneFullCategoryGridScreen(viewModel, onMovieClick, onBack)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvFullCategoryGridScreen(
    viewModel: FullCategoryGridViewModel = hiltViewModel(),
    onMovieClick: (Movie) -> Unit,
    onBack: () -> Unit
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val brandColorLong by viewModel.brandColor.collectAsStateWithLifecycle()
    val providerColor = remember(brandColorLong) { Color(brandColorLong) }
    val deviceClass = LocalDeviceClass.current
    val gridFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

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
        brandColor = providerColor,
        focusedColor = providerColor,
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
                if (deviceClass == DeviceClass.HIGH) {
                    Surface(
                        onClick = onBack,
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            focusedContainerColor = Color.White,
                            contentColor = Color.White,
                            focusedContentColor = Color.Black
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    Surface(
                        onClick = onBack,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = SurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = viewModel.categoryTitle,
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = error!!,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.tv.material3.Button(
                                onClick = { viewModel.retry() },
                                colors = androidx.tv.material3.ButtonDefaults.colors(
                                    containerColor = providerColor
                                )
                            ) {
                                Text("Повторити", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                itemsIndexed(items, key = { _, movie -> movie.id }, contentType = { _, _ -> "movie" }) { index, movie ->
                    val onClick = remember(movie.id) { { onMovieClick(movie) } }
                    CompactCategoryCard(
                        movie = movie,
                        onClick = onClick,
                        entranceIndex = index,
                        entranceTrigger = entranceTrigger,
                        deviceClass = deviceClass,
                        gridState = gridState
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CompactCategoryCard(
    movie: Movie,
    onClick: () -> Unit,
    entranceIndex: Int = 0,
    entranceTrigger: Long = 0L,
    deviceClass: DeviceClass = DeviceClass.MID,
    gridState: LazyGridState? = null
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isMediatek = LocalIsMediatek.current

    val staggerMs = when (deviceClass) {
        DeviceClass.HIGH -> 30
        DeviceClass.MID -> 20
        DeviceClass.LOW -> 0
    }
    val animDuration = when (deviceClass) {
        DeviceClass.HIGH -> 200
        DeviceClass.MID -> 150
        DeviceClass.LOW -> 0
    }
    var itemVisible by remember(entranceTrigger, entranceIndex) { mutableStateOf(deviceClass == DeviceClass.LOW) }
    LaunchedEffect(entranceTrigger, entranceIndex) {
        if (deviceClass != DeviceClass.LOW) {
            val isInitialVisible = gridState == null || entranceIndex <= gridState.layoutInfo.visibleItemsInfo.size
            if (!isInitialVisible) {
                itemVisible = true
                return@LaunchedEffect
            }
            itemVisible = true
        }
    }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (itemVisible) 1f else 0f,
        animationSpec = if (deviceClass == DeviceClass.LOW) {
            androidx.compose.animation.core.snap()
        } else {
            tween(durationMillis = animDuration)
        },
        label = "entranceAlpha"
    )
    val entranceScale by animateFloatAsState(
        targetValue = if (itemVisible) 1f else 0.95f,
        animationSpec = if (deviceClass == DeviceClass.HIGH) {
            spring(dampingRatio = 0.7f, stiffness = 300f)
        } else {
            androidx.compose.animation.core.snap()
        },
        label = "entranceScale"
    )

    val focusScale = if (isFocused) 1.05f else 1f

    val (gridW, gridH) = when (deviceClass) {
        DeviceClass.LOW -> 180 to 270
        DeviceClass.MID -> 300 to 450
        DeviceClass.HIGH -> 360 to 540
    }
    val imageRequest = remember(movie.poster, deviceClass) {
        ImageRequest.Builder(context)
            .data(movie.poster)
            .size(gridW, gridH)
            .deviceImage(deviceClass, isMediatek)
            .build()
    }

    val displayScale = entranceScale * focusScale

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = displayScale
                scaleY = displayScale
                alpha = entranceAlpha
            }
    ) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF141414),
                focusedContainerColor = Color(0xFF1E1E1E)
            ),
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .fillMaxWidth()
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    placeholder = PlaceholderDark,
                    error = PlaceholderDark
                )

                if (movie.provider != null) {
                    val providerColor = when (movie.provider) {
                        "Uakino" -> Color(0xFFFF6B35)
                        "Eneyida" -> Color(0xFF4ECDC4)
                        else -> Color(0xFF888888)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(providerColor.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = movie.provider.uppercase(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Text(
            text = movie.title,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
    }
}

@Composable
private fun PhoneFullCategoryGridScreen(
    viewModel: FullCategoryGridViewModel = hiltViewModel(),
    onMovieClick: (Movie) -> Unit,
    onBack: () -> Unit
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val brandColorLong by viewModel.brandColor.collectAsStateWithLifecycle()
    val providerColor = remember(brandColorLong) { Color(brandColorLong) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(viewModel.categoryTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (!isLoading) {
                Spacer(Modifier.width(8.dp))
                Text("(${items.size})", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
            }
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = providerColor)
                }
            }
            error != null && items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.Button(onClick = { viewModel.retry() }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = providerColor)) {
                            Text("Повторити")
                        }
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(PhoneGridDefaults.columns),
                    horizontalArrangement = Arrangement.spacedBy(PhoneGridDefaults.columnSpacing),
                    verticalArrangement = Arrangement.spacedBy(PhoneGridDefaults.rowSpacing),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(items, key = { _, it -> it.id }, contentType = { _, _ -> "movie" }) { index, movie ->
                        val onClick = remember(movie.id) { { onMovieClick(movie) } }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClick() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF141414))
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(movie.poster)
                                        .size(PhoneCardDefaults.posterWidth.value.toInt(), PhoneCardDefaults.posterHeight.value.toInt())
                                        .build(),
                                    contentDescription = movie.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                                    placeholder = ColorPainter(Color(0xFF1A1A1D)),
                                    error = ColorPainter(Color(0xFF1A1A1D))
                                )
                                if (movie.provider != null) {
                                    val pColor = when (movie.provider) {
                                        "Uakino" -> Color(0xFFFF6B35)
                                        "Eneyida" -> Color(0xFF4ECDC4)
                                        else -> Color(0xFF888888)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .background(pColor.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(movie.provider.uppercase(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, maxLines = 1)
                                    }
                                }
                            }
                            Text(movie.title, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
