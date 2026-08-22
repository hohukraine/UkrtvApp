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
                    TvDetailContent(
                        uiState = uiState,
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
