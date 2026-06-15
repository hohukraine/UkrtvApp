package ua.ukrtv.app.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
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
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.StreamResolutionResult
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.tv.material3.ExperimentalTvMaterial3Api

import ua.ukrtv.app.ui.components.SeasonEpisodePicker

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(
    onPlayClick: (StreamResolutionResult, String?, Int?, Int?, String, List<Season>?) -> Unit,
    onBackClick: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val watchState by viewModel.watchState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { request ->
            onPlayClick(
                request.streamResult,
                request.title,
                request.season,
                request.episode,
                request.contentId,
                request.seasons
            )
        }
    }

    val backgroundColor = remember { Color(0xFF0C0C0D) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor) // backgroundMinimal
    ) {
        when (val s = state) {
            is DetailState.Loading -> {
                val loadingColor = remember { Color(0xFF6E85B7) }
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = loadingColor // accentMinimal
                )
            }
            is DetailState.Success -> {
                DetailContent(
                    state = s,
                    watchState = watchState,
                    isFavorite = isFavorite,
                    onWatchClick = { viewModel.watchContent() },
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    onEpisodeClick = { s_num, e_num -> viewModel.watchContent(s_num, e_num) }
                )
            }
            is DetailState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.message, color = Color(0xFFE57373))
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
    watchState: WatchState,
    isFavorite: Boolean,
    onWatchClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEpisodeClick: (Int, Int) -> Unit
) {
    val posterUrl = state.posterPath
    val context = LocalContext.current
    val imageRequest = remember(posterUrl) {
        ImageRequest.Builder(context).data(posterUrl).crossfade(true).build()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.15f
        )

        val gradientBrush = remember {
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0xFF0C0C0D)),
                startY = 0f,
                endY = 1200f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 72.dp, vertical = 64.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val posterShape = remember { RoundedCornerShape(12.dp) }
                    val posterBgColor = remember { Color(0xFF151517) }
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .aspectRatio(2f / 3f)
                            .clip(posterShape)
                            .background(posterBgColor)
                    ) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = state.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(
                        modifier = Modifier
                            .padding(start = 64.dp)
                            .weight(1f)
                    ) {
                        val titleColor = remember { Color(0xFFE1E1E1) }
                        Text(
                            text = state.title,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Light,
                            color = titleColor,
                            lineHeight = 46.sp
                        )

                        Row(
                            modifier = Modifier.padding(vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val releaseDateColor = remember { Color(0xFF7A7A7A) }
                            val contentTypeColor = remember { Color(0xFF6E85B7).copy(alpha = 0.7f) }
                            Text(
                                text = state.releaseDate ?: "",
                                color = releaseDateColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraLight
                            )
                            Spacer(modifier = Modifier.width(32.dp))
                            Text(
                                text = if (state.type == ContentType.MOVIE) "ФІЛЬМ" else "СЕРІАЛ",
                                color = contentTypeColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 2.sp
                            )
                        }

                        val overviewColor = remember { Color(0xFFE1E1E1).copy(alpha = 0.7f) }
                        Text(
                            text = state.overview,
                            fontSize = 16.sp,
                            color = overviewColor,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 26.sp,
                            modifier = Modifier.padding(bottom = 40.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            var isWatchFocused by remember { mutableStateOf(false) }
                            val buttonColors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = Color(0xFF6E85B7),
                                focusedContainerColor = Color(0xFFE1E1E1),
                                contentColor = Color.White,
                                focusedContentColor = Color(0xFF0C0C0D)
                            )
                            val buttonShape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(8.dp))

                            androidx.tv.material3.Button(
                                onClick = onWatchClick,
                                enabled = watchState !is WatchState.Searching,
                                modifier = Modifier
                                    .onFocusChanged { isWatchFocused = it.isFocused },
                                colors = buttonColors,
                                shape = buttonShape,
                                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp)
                            ) {
                                if (watchState is WatchState.Searching) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Text(
                                        text = if (watchState is WatchState.NotFound) "НЕ ЗНАЙДЕНО (ПОВТОРИТИ)" else "ДИВИТИСЯ",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }

                        if (watchState is WatchState.NotFound) {
                            Text(
                                "Не вдалося отримати посилання на відео. Спробуйте іншого провайдера або пізніше.",
                                color = Color(0xFFE57373),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }

                        if (!state.detail.seasons.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(48.dp))
                            SeasonEpisodePicker(
                                seasons = state.detail.seasons,
                                onEpisodeClick = onEpisodeClick
                            )
                        }
                    }
                }
            }
        }
    }
}
