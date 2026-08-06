package ua.ukrtv.app.ui.detail

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.graphics.painter.ColorPainter
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MediaLaunchState
import ua.ukrtv.app.domain.model.Episode
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import ua.ukrtv.app.ui.components.DetailSkeleton
import ua.ukrtv.app.ui.home.components.ContentRow
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.ui.theme.DetailDefaults
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.ui.theme.deviceImage
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.Shapes
import ua.ukrtv.app.ui.theme.Error
import ua.ukrtv.app.ui.theme.PlaceholderDark
import ua.ukrtv.app.ui.components.RatingCircle
import ua.ukrtv.app.ui.components.parseRating
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.util.PosterColorCache
import ua.ukrtv.app.util.PerformanceProfile
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.LocalFormFactor
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil3.compose.rememberAsyncImagePainter
import ua.ukrtv.app.ui.theme.PosterStyle
import ua.ukrtv.app.ui.theme.ProviderSizes
import ua.ukrtv.app.ui.theme.PhoneCardDefaults

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DetailScreen(
    onMovieClick: (Movie) -> Unit = {},
    onPlayClick: (MediaLaunchState) -> Unit,
    onBackClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formFactor = LocalFormFactor.current

    LaunchedEffect(uiState.launchState) {
        if (uiState.launchState is MediaLaunchState.Ready) {
            onPlayClick(uiState.launchState)
            viewModel.resetLaunchState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        when (val s = uiState.detailState) {
            is DetailState.Loading -> {
                DetailSkeleton()
            }
            is DetailState.Success -> {
                if (formFactor == FormFactor.PHONE) {
                    PhoneDetailContent(
                        uiState = uiState,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        onMovieClick = onMovieClick,
                        onWatchClick = { viewModel.watchContent() },
                        onEpisodeClick = { s_num, ep, vo -> viewModel.watchContent(season = s_num, episode = ep.number, voiceover = vo) },
                        onBackClick = onBackClick,
                        onToggleWatchlist = { viewModel.toggleWatchlist() }
                    )
                } else {
                    DetailContent(
                        uiState = uiState,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        onMovieClick = onMovieClick,
                        onWatchClick = { viewModel.watchContent() },
                        onEpisodeClick = { s_num, ep, vo -> viewModel.watchContent(season = s_num, episode = ep.number, voiceover = vo) },
                        onBackClick = onBackClick,
                        onToggleWatchlist = { viewModel.toggleWatchlist() }
                    )
                }
            }
            is DetailState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.error.message, color = Error)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.tv.material3.Button(onClick = { viewModel.retry() }) {
                            Text("Повторити", color = Color.White)
                        }
                        androidx.tv.material3.Button(onClick = onBackClick) {
                            Text("Назад", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DetailContent(
    uiState: DetailUiState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    onMovieClick: (Movie) -> Unit,
    onWatchClick: () -> Unit,
    onEpisodeClick: (Int, Episode, String?) -> Unit,
    onBackClick: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val state = uiState.detailState as? DetailState.Success ?: return
    val detail = state.detail
    val launchState = uiState.launchState
    val isInWatchlist = uiState.isInWatchlist
    val context = LocalContext.current
    val deviceClass = LocalDeviceClass.current
    val brandColor = remember(detail.brandColor) {
        try { Color(android.graphics.Color.parseColor(detail.brandColor)) } catch (_: Exception) { BrandBlue }
    }

    val providerColor = remember(detail.brandColor) {
        brandColor
    }

    val isMediatek = LocalIsMediatek.current
    val posterStyle = remember(detail.providerName) {
        PosterStyle.forProvider(detail.providerName)
    }
    val detailDims = ProviderSizes.detailPoster(posterStyle)
    
    val (posterW, posterH) = when (posterStyle) {
        PosterStyle.WIDE -> when (deviceClass) {
            DeviceClass.LOW -> 400 to 225
            DeviceClass.MID -> 800 to 450
            DeviceClass.HIGH -> 1200 to 675
        }
        PosterStyle.VERTICAL -> when (deviceClass) {
            DeviceClass.LOW -> 230 to 345
            DeviceClass.MID -> 460 to 690
            DeviceClass.HIGH -> 690 to 1035
        }
    }
    val posterRequest = remember(detail.poster, deviceClass, posterStyle) {
        if (detail.poster.isBlank()) return@remember null
        ImageRequest.Builder(context)
            .data(detail.poster)
            .size(posterW, posterH)
            .deviceImage(deviceClass, isMediatek)
            .build()
    }

    var backdropColor by remember { mutableStateOf(Background) }
    LaunchedEffect(detail.poster) {
        backdropColor = PosterColorCache.getColor(context, detail.poster, fallback = Background)
    }

    val disableBackdropAnim = deviceClass == DeviceClass.LOW || isMediatek
    val animatedBackdropColor by animateColorAsState(
        targetValue = backdropColor,
        animationSpec = if (disableBackdropAnim) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "backdropColor"
    )

    val scrollState = rememberLazyListState()
    val disableMotion = deviceClass == DeviceClass.LOW || isMediatek
    val isWideStyle = posterStyle == PosterStyle.WIDE

    // Staggered entrance animations
    val entranceProgress = remember { Animatable(0f) }
    LaunchedEffect(detail.id) {
        if (deviceClass != DeviceClass.LOW) {
            entranceProgress.animateTo(1f, tween(1000, easing = EaseOutCubic))
        } else {
            entranceProgress.snapTo(1f)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "detailMotion")
    val periodX = if (deviceClass == DeviceClass.HIGH) 4000 else 6000
    val periodY = if (deviceClass == DeviceClass.HIGH) 5000 else 8000

    val driftXState = if (disableMotion) {
        remember { mutableStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(periodX, easing = LinearEasing)
            ),
            label = "driftX"
        )
    }

    val driftYState = if (disableMotion) {
        remember { mutableStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(periodY, easing = LinearEasing)
            ),
            label = "driftY"
        )
    }

    // Cinematic backdrop for WIDE style (Netflix look)
    if (isWideStyle) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = posterRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .graphicsLayer {
                        alpha = (entranceProgress.value * 0.7f).coerceIn(0f, 0.7f)
                    }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Background.copy(alpha = 0.5f),
                                Background
                            ),
                            startY = 0f,
                            endY = context.resources.displayMetrics.heightPixels.toFloat() * 0.7f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Background,
                                Background.copy(alpha = 0.8f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = context.resources.displayMetrics.widthPixels.toFloat() * 0.6f
                        )
                    )
            )
        }
    }

    // Premium color-only backdrop for VERTICAL style
    if (!isWideStyle) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val driftX = if (disableMotion) 0f else sin(driftXState.value.toDouble()).toFloat()
                    val driftY = if (disableMotion) 0f else sin(driftYState.value.toDouble()).toFloat()

                    val driftAmount = if (disableMotion) 0f else when (deviceClass) {
                        DeviceClass.LOW -> 0f
                        DeviceClass.MID -> 0.015f
                        DeviceClass.HIGH -> 0.03f
                    }
                    val dx = driftX * driftAmount * w
                    val dy = driftY * driftAmount * h

                    // Layer 1: Radial glow from top-center (poster-derived color)
                    val glowAlpha = when (deviceClass) {
                        DeviceClass.LOW -> 0.15f
                        DeviceClass.MID -> 0.35f
                        DeviceClass.HIGH -> 0.55f
                    }
                    val glowRadius = when (deviceClass) {
                        DeviceClass.LOW -> w * 0.8f
                        DeviceClass.MID -> w * 1.2f
                        DeviceClass.HIGH -> w * 1.5f
                    }
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedBackdropColor.copy(alpha = glowAlpha),
                                animatedBackdropColor.copy(alpha = glowAlpha * 0.3f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(
                                w * 0.5f + dx,
                                h * 0.1f + dy * 0.5f
                            ),
                            radius = glowRadius
                        )
                    )

                    // Layer 2: Vertical gradient from backdrop to Background
                    val gradEnd = if (deviceClass == DeviceClass.HIGH) h * 0.7f else h * 0.5f
                    val topAlpha = when (deviceClass) {
                        DeviceClass.LOW -> 0.1f
                        DeviceClass.MID -> 0.2f
                        DeviceClass.HIGH -> 0.35f
                    }
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                animatedBackdropColor.copy(alpha = topAlpha),
                                animatedBackdropColor.copy(alpha = topAlpha * 0.5f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = gradEnd
                        )
                    )

                    // Layer 3: Provider glow (bottom-left, complementary to top-center backdrop)
                    val providerGlowAlpha = when (deviceClass) {
                        DeviceClass.LOW -> 0.03f
                        DeviceClass.MID -> 0.06f
                        DeviceClass.HIGH -> 0.10f
                    }
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                providerColor.copy(alpha = providerGlowAlpha),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(
                                w * 0.15f + dx * 0.5f,
                                h * 0.85f + dy * 0.3f
                            ),
                            radius = w * 0.8f
                        )
                    )

                    // Layer 4 (HIGH only): Second glow from bottom-right for depth
                    if (deviceClass == DeviceClass.HIGH) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    animatedBackdropColor.copy(alpha = 0.1f),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(
                                    w * 0.85f - dx * 0.3f,
                                    h * 0.85f + dy * 0.4f
                                ),
                                radius = w * 0.7f
                            )
                        )
                    }
                }
        )
    }

    // Content
    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = DetailDefaults.horizontalPadding,
            end = DetailDefaults.horizontalPadding,
            top = 32.dp,
            bottom = 100.dp
        )
    ) {
        item(key = "back_button", contentType = "back_button") {
            Column {
                // Back button
                Surface(
                    onClick = onBackClick,
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
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
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        item(key = "main_info", contentType = "main_info") {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Poster card (Only for Vertical style)
                if (!isWideStyle) {
                    Surface(
                        onClick = {},
                        shape = ClickableSurfaceDefaults.shape(Shapes.card),
                        modifier = Modifier
                            .width(detailDims.width)
                            .height(detailDims.height)
                            .then(
                                if (sharedTransitionScope != null && animatedContentScope != null) {
                                    with(sharedTransitionScope) {
                                        Modifier.sharedElement(
                                            rememberSharedContentState(key = "movie_poster_${detail.id}"),
                                            animatedVisibilityScope = animatedContentScope
                                        )
                                    }
                                } else Modifier
                            )
                            .graphicsLayer {
                                alpha = (entranceProgress.value * 2f).coerceIn(0f, 1f)
                                val s = 1.1f - (alpha * 0.1f)
                                scaleX = s
                                scaleY = s
                            }
                            .shadow(24.dp, Shapes.card),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent)
                    ) {
                        AsyncImage(
                            model = posterRequest,
                            contentDescription = detail.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = PlaceholderDark,
                            error = PlaceholderDark
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(start = if (isWideStyle) 0.dp else 56.dp)
                        .weight(1f)
                        .graphicsLayer {
                            alpha = ((entranceProgress.value - 0.2f) * 3f).coerceIn(0f, 1f)
                            translationY = (1f - alpha) * 20.dp.toPx()
                        }
                ) {
                    // Title
                    val titleSize = when (deviceClass) {
                        DeviceClass.LOW -> 36.sp
                        DeviceClass.MID -> 40.sp
                        DeviceClass.HIGH -> 48.sp
                    }
                    Text(
                        text = detail.title.uppercase(),
                        fontSize = titleSize,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp,
                        lineHeight = titleSize * 1.2f
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Metadata Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        RatingCircle(rating = parseRating(detail.rating))

                        if (detail.year != null) {
                            Text(detail.year.toString(), color = OnSurface.copy(alpha = 0.7f), fontSize = 16.sp)
                        }

                        if (!detail.duration.isNullOrEmpty()) {
                            Text(detail.duration, color = OnSurface.copy(alpha = 0.7f), fontSize = 16.sp)
                        }

                        Text(
                            text = if (detail.seasons == null) "MOVIE" else "SERIES",
                            color = brandColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Actions Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer {
                            alpha = ((entranceProgress.value - 0.35f) * 3f).coerceIn(0f, 1f)
                            translationY = (1f - alpha) * 15.dp.toPx()
                        }
                    ) {
                        val isResolving = launchState is MediaLaunchState.Resolving
                        val interactionSource = remember { MutableInteractionSource() }
                        val isBtnFocused by interactionSource.collectIsFocusedAsState()

                        // Play button
                        Surface(
                            onClick = onWatchClick,
                            interactionSource = interactionSource,
                            shape = ClickableSurfaceDefaults.shape(Shapes.chip),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = brandColor,
                                focusedContainerColor = Color.White,
                                contentColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                            modifier = Modifier
                                .then(
                                    if (isBtnFocused) {
                                        Modifier.graphicsLayer {
                                            shadowElevation = 20.dp.toPx()
                                            ambientShadowColor = brandColor.copy(alpha = 0.5f)
                                            spotShadowColor = brandColor.copy(alpha = 0.3f)
                                        }
                                    } else Modifier
                                )
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isResolving) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("ЗАВАНТАЖЕННЯ...", fontWeight = FontWeight.Black, fontSize = 14.sp)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text("ДИВИТИСЯ", fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
                                    }
                                }
                            }
                        }

                        // Watchlist button
                        Surface(
                            onClick = onToggleWatchlist,
                            shape = ClickableSurfaceDefaults.shape(Shapes.chip),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                focusedContainerColor = Color.White,
                                contentColor = Color.White,
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isInWatchlist) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("MY LIST", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }
                    }

                    // Genre chips
                    if (detail.genres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.graphicsLayer { 
                                alpha = ((entranceProgress.value - 0.5f) * 3f).coerceIn(0f, 1f) 
                            }
                        ) {
                            detail.genres.take(4).forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .clip(Shapes.chip)
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(genre, color = OnSurface, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // Meta info rows
                    Column(modifier = Modifier.graphicsLayer { 
                        alpha = ((entranceProgress.value - 0.5f) * 3f).coerceIn(0f, 1f) 
                    }) {
                        if (detail.country.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            MetaRow(label = "Країна", values = detail.country, brandColor = brandColor)
                        }
                        if (detail.director.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            MetaRow(label = "Режисер", values = detail.director, brandColor = brandColor)
                        }
                        if (detail.actors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            MetaRow(label = "Актори", values = detail.actors.take(5), brandColor = brandColor)
                        }

                        // Description
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = detail.description.ifEmpty { "Опис відсутній" },
                            fontSize = 16.sp,
                            color = OnSurface.copy(alpha = 0.8f),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 26.sp
                        )
                    }
                }
            }
        }

        // Actors horizontal scroll
        if (detail.actors.isNotEmpty()) {
            item(key = "actors_row", contentType = "actors_row") {
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = "АКТОРИ",
                    color = brandColor.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(detail.actors.take(12), key = { it }, contentType = { "actor" }) { actor ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(90.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = actor.take(2).uppercase(),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = actor,
                                color = OnSurface.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Seasons
        if (!detail.seasons.isNullOrEmpty()) {
            item(key = "seasons", contentType = "seasons") {
                Column {
                    Spacer(modifier = Modifier.height(64.dp))
                    SeasonEpisodePicker(
                        seasons = detail.seasons,
                        onEpisodeClick = onEpisodeClick,
                        accentColor = brandColor
                    )
                }
            }
        }

        // Comments
        if (detail.comments.isNotEmpty()) {
            item(key = "comments", contentType = "comments") {
                Column {
                    Spacer(modifier = Modifier.height(64.dp))
                    CommentsSection(
                        comments = detail.comments,
                        providerName = detail.providerName,
                        accentColor = brandColor
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, values: List<String>, brandColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            color = brandColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = values.joinToString(", "),
            color = OnSurface.copy(alpha = 0.8f),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PhoneDetailContent(
    uiState: DetailUiState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    onMovieClick: (Movie) -> Unit,
    onWatchClick: () -> Unit,
    onEpisodeClick: (Int, Episode, String?) -> Unit,
    onBackClick: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val state = uiState.detailState as? DetailState.Success ?: return
    val detail = state.detail
    val launchState = uiState.launchState
    val isInWatchlist = uiState.isInWatchlist
    val performanceProfile = uiState.performanceProfile
    val context = LocalContext.current
    val brandColor = remember(detail.brandColor) {
        try { Color(android.graphics.Color.parseColor(detail.brandColor)) } catch (_: Exception) { BrandBlue }
    }

    val isPerformanceMode = performanceProfile == PerformanceProfile.PERFORMANCE
    val shouldBlur = !isPerformanceMode

    val posterStyle = remember(detail.providerName) {
        PosterStyle.forProvider(detail.providerName)
    }
    val phoneDetailDims = ProviderSizes.phoneCard(posterStyle)

    val posterRequest = remember(detail.poster, posterStyle) {
        if (detail.poster.isBlank()) return@remember null
        val (iw, ih) = when (posterStyle) {
            PosterStyle.WIDE -> 640 to 360
            PosterStyle.VERTICAL -> 480 to 720
        }
        ImageRequest.Builder(context)
            .data(detail.poster)
            .size(iw, ih)
            .crossfade(100)
            .build()
    }

    var showFullDescription by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()
    val scrollFraction by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex > 0) 1f
            else {
                val offset = scrollState.firstVisibleItemScrollOffset
                (offset / 800f).coerceIn(0f, 1f)
            }
        }
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item(key = "hero", contentType = "hero") {
            // Hero banner — poster with gradient overlay
            val isWide = posterStyle == PosterStyle.WIDE
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isWide) Modifier.aspectRatio(16f / 9f) else Modifier.height(340.dp))
                    .drawBehind {
                        val w = size.width
                        val h = size.height
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    brandColor.copy(alpha = 0.4f),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.5f),
                                radius = w * 0.8f
                            )
                        )
                    }
            ) {
                // Poster as background
                AsyncImage(
                    model = posterRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (sharedTransitionScope != null && animatedContentScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedElement(
                                        rememberSharedContentState(key = "movie_poster_${detail.id}"),
                                        animatedVisibilityScope = animatedContentScope
                                    )
                                }
                            } else Modifier
                        )
                        .graphicsLayer { translationY = scrollFraction * 120f }
                        .then(if (shouldBlur) Modifier.blur(20.dp, 20.dp) else Modifier)
                )

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Transparent,
                                    Background.copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                // Back button
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(12.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(4.dp)
                        .clickable { onBackClick() }
                )

                // Title at bottom of hero
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = detail.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 30.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RatingCircle(rating = parseRating(detail.rating))
                        if (detail.year != null) {
                            Text(detail.year.toString(), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        }
                        if (!detail.duration.isNullOrEmpty()) {
                            Text(detail.duration, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        item(key = "actions", contentType = "actions") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(16.dp))
                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isResolving = launchState is MediaLaunchState.Resolving
                    Button(
                        onClick = onWatchClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isResolving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ЗАВАНТАЖЕННЯ...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("ДИВИТИСЯ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onToggleWatchlist,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isInWatchlist) brandColor else OnSurface.copy(alpha = 0.7f)
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = SolidColor(if (isInWatchlist) brandColor else OnSurface.copy(alpha = 0.3f))
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isInWatchlist) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("ОБРАНЕ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item(key = "meta", contentType = "meta") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Genre chips
                if (detail.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        detail.genres.take(4).forEach { genre ->
                            Box(
                                modifier = Modifier
                                    .clip(Shapes.chip)
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(genre, color = OnSurface, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Meta info
                if (detail.country.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    MetaRow(label = "Країна", values = detail.country, brandColor = brandColor)
                }
                if (detail.director.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    MetaRow(label = "Режисер", values = detail.director, brandColor = brandColor)
                }
                if (detail.actors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    MetaRow(label = "Актори", values = detail.actors.take(5), brandColor = brandColor)
                }

                // Description
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = detail.description.ifEmpty { "Опис відсутній" },
                    fontSize = 14.sp,
                    color = OnSurface.copy(alpha = 0.8f),
                    maxLines = if (showFullDescription) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )
                if (detail.description.length > 200) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val onToggleDescription = remember { { showFullDescription = !showFullDescription } }
                    Text(
                        text = if (showFullDescription) "Згорнути" else "Розгорнути",
                        color = brandColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onToggleDescription() }
                    )
                }
            }
        }

        if (!detail.seasons.isNullOrEmpty()) {
            item(key = "seasons", contentType = "seasons") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(24.dp))
                    SeasonEpisodePicker(
                        seasons = detail.seasons,
                        onEpisodeClick = onEpisodeClick,
                        accentColor = brandColor
                    )
                }
            }
        }

        if (detail.comments.isNotEmpty()) {
            item(key = "comments", contentType = "comments") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(24.dp))
                    CommentsSection(
                        comments = detail.comments,
                        providerName = detail.providerName,
                        accentColor = brandColor
                    )
                }
            }
        }
    }
}
