package ua.ukrtv.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.Episode
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import ua.ukrtv.app.ui.detail.SeasonEpisodePicker
import ua.ukrtv.app.ui.detail.CommentsSection

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(
    onPlayClick: (MediaLaunchState) -> Unit,
    onBackClick: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
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
            .background(Color(0xFF0C0C0D))
    ) {
        when (val s = state) {
            is DetailState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF6E85B7)
                )
            }
            is DetailState.Success -> {
                DetailContent(
                    state = s,
                    launchState = launchState,
                    onWatchClick = { viewModel.watchContent() },
                    onEpisodeClick = { s_num, ep -> viewModel.watchContent(season = s_num, episode = ep.number) },
                    onBackClick = onBackClick
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
    launchState: MediaLaunchState,
    onWatchClick: () -> Unit,
    onEpisodeClick: (Int, Episode) -> Unit,
    onBackClick: () -> Unit
) {
    val detail = state.detail
    val context = LocalContext.current
    val brandColor = remember(detail.brandColor) {
        try { Color(android.graphics.Color.parseColor(detail.brandColor)) } catch (_: Exception) { Color(0xFF6E85B7) }
    }
    val imageRequest = remember(detail.poster) {
        ImageRequest.Builder(context)
            .data(detail.poster)
            .size(1280, 720)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .crossfade(false)
            .build()
    }
    val posterRequest = remember(detail.poster) {
        ImageRequest.Builder(context)
            .data(detail.poster)
            .size(300, 450)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .crossfade(false)
            .build()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.15f
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF0C0C0D)),
                        startY = 0f,
                        endY = 1200f
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 72.dp, vertical = 64.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF151517))
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
                            .padding(start = 64.dp)
                            .weight(1f)
                    ) {
                        Text(
                            text = detail.title,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Light,
                            color = Color(0xFFE1E1E1),
                            lineHeight = 46.sp
                        )

                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!detail.rating.isNullOrEmpty()) {
                                androidx.tv.material3.Surface(
                                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                                        containerColor = Color(0xFFDAA520)
                                    ),
                                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                                    onClick = {}
                                ) {
                                    Text(
                                        text = detail.rating!!,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = Color.Black,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            
                            Text(
                                text = detail.year ?: "",
                                color = Color(0xFF7A7A7A),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraLight
                            )
                            Spacer(modifier = Modifier.width(32.dp))
                            Text(
                                text = if (detail.contentType == ContentType.MOVIE) "ФІЛЬМ" else "СЕРІАЛ",
                                color = Color(0xFF6E85B7).copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 2.sp
                            )
                        }

                        if (detail.genres.isNotEmpty() || detail.country.isNotEmpty()) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                if (detail.genres.isNotEmpty()) {
                                    DetailInfoRow("Жанр", detail.genres.joinToString(", "))
                                }
                                if (detail.country.isNotEmpty()) {
                                    DetailInfoRow("Країна", detail.country.joinToString(", "))
                                }
                                if (detail.director.isNotEmpty()) {
                                    DetailInfoRow("Режисер", detail.director.joinToString(", "))
                                }
                                if (detail.actors.isNotEmpty()) {
                                    DetailInfoRow("Актори", detail.actors.joinToString(", "), maxLines = 1)
                                }
                            }
                        }

                        Text(
                            text = detail.description,
                            fontSize = 16.sp,
                            color = Color(0xFFE1E1E1).copy(alpha = 0.7f),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            val isResolving = launchState is MediaLaunchState.Resolving
                            
                            androidx.tv.material3.Button(
                                onClick = onWatchClick,
                                colors = androidx.tv.material3.ButtonDefaults.colors(
                                    containerColor = Color(0xFF6E85B7),
                                    focusedContainerColor = Color(0xFFE1E1E1),
                                    contentColor = Color.White,
                                    focusedContentColor = Color(0xFF0C0C0D)
                                ),
                                shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp)
                            ) {
                                if (isResolving) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("РЕЗОЛВІНГ...", fontSize = 14.sp)
                                    }
                                } else {
                                    Text("ДИВИТИСЯ", fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                                }
                            }
                        }

                        if (launchState is MediaLaunchState.Error) {
                            Text(
                                launchState.message,
                                color = Color(0xFFE57373),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }

                        if (!detail.seasons.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(48.dp))
                            SeasonEpisodePicker(
                                seasons = detail.seasons,
                                onEpisodeClick = onEpisodeClick,
                                accentColor = brandColor
                            )
                            Spacer(modifier = Modifier.height(48.dp))
                        }

                        if (detail.comments.isNotEmpty()) {
                            CommentsSection(
                                comments = detail.comments,
                                providerName = detail.providerName,
                                providerLogoUrl = if (detail.providerName == "Uakino")
                                    "https://uakino.best/templates/uakino/images/logo.png"
                                else null,
                                accentColor = brandColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailInfoRow(label: String, value: String, maxLines: Int = 2) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            color = Color(0xFF7A7A7A),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            color = Color(0xFFE1E1E1).copy(alpha = 0.8f),
            fontSize = 14.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
