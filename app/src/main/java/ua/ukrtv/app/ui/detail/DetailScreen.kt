package ua.ukrtv.app.ui.detail

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.domain.model.MediaLaunchState
import ua.ukrtv.app.domain.model.Episode
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import ua.ukrtv.app.ui.components.DetailSkeleton
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.ui.theme.DetailDefaults
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.Shapes
import ua.ukrtv.app.ui.theme.Error
import ua.ukrtv.app.ui.components.RatingCircle
import ua.ukrtv.app.ui.components.parseRating
import ua.ukrtv.app.util.PosterColorCache

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(
    onPlayClick: (MediaLaunchState) -> Unit,
    onBackClick: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val state by viewModel.state.collectAsState()
    val launchState by viewModel.launchState.collectAsState()

    LaunchedEffect(launchState) {
        if (launchState is MediaLaunchState.Ready) {
            onPlayClick(launchState)
            viewModel.resetLaunchState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        when (val s = state) {
            is DetailState.Loading -> {
                DetailSkeleton()
            }
            is DetailState.Success -> {
                DetailContent(
                    state = s,
                    launchState = launchState,
                    isInWatchlist = viewModel.isInWatchlist.collectAsState().value,
                    onWatchClick = { viewModel.watchContent() },
                    onEpisodeClick = { s_num, ep, vo -> viewModel.watchContent(season = s_num, episode = ep.number, voiceover = vo) },
                    onBackClick = onBackClick,
                    onToggleWatchlist = { viewModel.toggleWatchlist() },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
            is DetailState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.message, color = Error)
                    Spacer(modifier = Modifier.height(24.dp))
                    androidx.tv.material3.Button(onClick = onBackClick) {
                        Text("Назад", color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailContent(
    state: DetailState.Success,
    launchState: MediaLaunchState,
    isInWatchlist: Boolean,
    onWatchClick: () -> Unit,
    onEpisodeClick: (Int, Episode, String?) -> Unit,
    onBackClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val detail = state.detail
    val context = LocalContext.current
    val brandColor = remember(detail.brandColor) {
        try { Color(android.graphics.Color.parseColor(detail.brandColor)) } catch (_: Exception) { BrandBlue }
    }

    val posterRequest = remember(detail.poster) {
        ImageRequest.Builder(context)
            .data(detail.poster)
            .size(400, 600)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .build()
    }

    var backdropColor by remember { mutableStateOf(Background) }
    LaunchedEffect(detail.poster) {
        backdropColor = PosterColorCache.getColor(context, detail.poster, fallback = Background)
    }

    val animatedBackdropColor by animateColorAsState(
        targetValue = backdropColor,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "backdropColor"
    )

    val scrollState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Vertical gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            animatedBackdropColor.copy(alpha = 0.6f),
                            animatedBackdropColor.copy(alpha = 0.3f),
                            Background
                        ),
                        startY = 0f,
                        endY = size.height
                    )
                )
            }
    ) {
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
            item {
                Column {
                    androidx.tv.material3.Surface(
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Vertical Poster with Shadow and Shared Element
                    Surface(
                        onClick = {},
                        shape = ClickableSurfaceDefaults.shape(Shapes.card),
                        modifier = Modifier
                            .width(DetailDefaults.posterWidth)
                            .height(DetailDefaults.posterHeight)
                            .shadow(24.dp, Shapes.card)
                            .then(
                                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                    with(sharedTransitionScope) {
                                        val sharedContentState = rememberSharedContentState(key = "poster_${detail.id}")
                                        Modifier.sharedElement(sharedContentState = sharedContentState, animatedVisibilityScope = animatedVisibilityScope)
                                    }
                                } else Modifier
                            ),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent)
                    ) {
                        AsyncImage(
                            model = posterRequest,
                            contentDescription = detail.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(
                        modifier = Modifier
                            .padding(start = 56.dp)
                            .weight(1f)
                    ) {
                        // Title - PRO MAX Style
                        Text(
                            text = detail.title.uppercase(),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp,
                            lineHeight = 52.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Metadata Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Circular Rating (TMDB Style)
                            RatingCircle(rating = parseRating(detail.rating))

                            if (!detail.year.isNullOrEmpty()) {
                                Text(detail.year, color = OnSurface.copy(alpha = 0.7f), fontSize = 16.sp)
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

                        // Actions Row (Elevated)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val isResolving = launchState is MediaLaunchState.Resolving
                            val interactionSource = remember { MutableInteractionSource() }

                            androidx.tv.material3.Button(
                                onClick = onWatchClick,
                                interactionSource = interactionSource,
                                colors = androidx.tv.material3.ButtonDefaults.colors(
                                    containerColor = Color(0xFF3B82F6),
                                    focusedContainerColor = Color.White,
                                    contentColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = androidx.tv.material3.ButtonDefaults.shape(Shapes.chip),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isResolving) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("RESOLVING...", fontWeight = FontWeight.Black, fontSize = 13.sp)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("PLAY NOW", fontWeight = FontWeight.Black, fontSize = 13.sp)
                                    }
                                }
                            }

                            androidx.tv.material3.Surface(
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

                        if (detail.genres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = detail.description,
                            fontSize = 16.sp,
                            color = OnSurface.copy(alpha = 0.8f),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 26.sp
                        )
                    }
                }
            }

            if (!detail.seasons.isNullOrEmpty()) {
                item {
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

            if (detail.comments.isNotEmpty()) {
                item {
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
