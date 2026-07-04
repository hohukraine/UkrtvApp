package ua.ukrtv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.OnSurfaceVariant
import ua.ukrtv.app.ui.theme.Scrim
import ua.ukrtv.app.ui.theme.Shapes
import ua.ukrtv.app.ui.theme.Surface
import ua.ukrtv.app.ui.theme.SurfaceFocus
import ua.ukrtv.app.ui.theme.SurfaceVariant
import ua.ukrtv.app.ui.theme.FocusDefaults

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonSelectorScreen(
    onBack: () -> Unit,
    onEpisodeSelected: (SeasonSelectionResult) -> Unit,
    viewModel: SeasonSelectorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .onKeyEvent { event ->
                if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                    onBack()
                    return@onKeyEvent true
                }
                false
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 48.dp)
        ) {
            item {
                TopBar(
                    title = state.title,
                    onBack = onBack
                )
                Spacer(Modifier.height(24.dp))

                SeasonChips(
                    seasons = state.seasons,
                    selectedSeason = state.selectedSeason,
                    onSeasonSelected = viewModel::selectSeason
                )
            }

            if (!state.isLoading && state.seasons.isNotEmpty()) {
                val selectedSeason = state.seasons.find { it.number == state.selectedSeason }

                if (selectedSeason != null && selectedSeason.voiceoverOptions.size > 1) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        VoiceoverChips(
                            options = selectedSeason.voiceoverOptions,
                            selectedVoiceover = state.selectedVoiceover,
                            onVoiceoverSelected = viewModel::selectVoiceover
                        )
                    }
                }

                val episodes = selectedSeason?.voiceovers
                    ?.find { it.name == state.selectedVoiceover }
                    ?.episodes
                    ?: selectedSeason?.episodes
                    ?: emptyList()

                item {
                    Spacer(Modifier.height(24.dp))

                    if (episodes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Немає доступних серій",
                                color = OnSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        EpisodeGrid(
                            episodes = episodes.distinctBy { it.number }.sortedBy { it.number },
                            seasonNumber = state.selectedSeason,
                            currentSeason = state.currentSeason,
                            currentEpisode = state.currentEpisode,
                            onEpisodeClick = { episode ->
                                onEpisodeSelected(
                                    SeasonSelectionResult(
                                        season = state.selectedSeason,
                                        episode = episode.number,
                                        voiceover = state.selectedVoiceover
                                    )
                                )
                            }
                        )
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = BrandBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.tv.material3.Surface(
            onClick = onBack,
            shape = ClickableSurfaceDefaults.shape(Shapes.circular),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = SurfaceVariant,
                focusedContainerColor = BrandBlue,
                contentColor = OnSurface,
                focusedContentColor = Color.White
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = FocusDefaults.buttonScale),
            modifier = Modifier.size(44.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.width(20.dp))

        Text(
            text = title,
            color = OnSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonChips(
    seasons: List<Season>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(seasons, key = { it.number }) { season ->
            val isSelected = selectedSeason == season.number
            Surface(
                onClick = { onSeasonSelected(season.number) },
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) BrandBlue else SurfaceVariant,
                    focusedContainerColor = BrandBlue,
                    contentColor = if (isSelected) Color.White else OnSurfaceVariant,
                    focusedContentColor = Color.White
                ),
                shape = ClickableSurfaceDefaults.shape(Shapes.chip),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) BrandBlue else SurfaceFocus
                        )
                    ),
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            FocusDefaults.borderWidth,
                            BrandBlue
                        )
                    )
                ),
                modifier = Modifier.onFocusChanged { focused ->
                    if (focused.isFocused) onSeasonSelected(season.number)
                }
            ) {
                Text(
                    text = "Сезон ${season.number}",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VoiceoverChips(
    options: List<String>,
    selectedVoiceover: String?,
    onVoiceoverSelected: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(options, key = { it }) { voiceover ->
            val isSelected = selectedVoiceover == voiceover
            Surface(
                onClick = { onVoiceoverSelected(voiceover) },
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) BrandBlue.copy(alpha = 0.3f) else Surface,
                    focusedContainerColor = BrandBlue.copy(alpha = 0.3f),
                    contentColor = if (isSelected) BrandBlue else OnSurfaceVariant,
                    focusedContentColor = BrandBlue
                ),
                shape = ClickableSurfaceDefaults.shape(Shapes.chip),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) BrandBlue.copy(alpha = 0.6f) else SurfaceFocus
                        )
                    ),
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            FocusDefaults.borderWidth,
                            BrandBlue.copy(alpha = 0.6f)
                        )
                    )
                )
            ) {
                Text(
                    text = voiceover,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeGrid(
    episodes: List<Episode>,
    seasonNumber: Int,
    currentSeason: Int?,
    currentEpisode: Int?,
    onEpisodeClick: (Episode) -> Unit
) {
    val gridFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos {
            gridFocusRequester.requestFocus()
        }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        episodes.forEach { episode ->
            val isCurrent = seasonNumber == (currentSeason ?: -1) && episode.number == (currentEpisode ?: -1)
            val isFirst = episode == episodes.first()

            EpisodeGridCard(
                episode = episode,
                isCurrent = isCurrent,
                onClick = { onEpisodeClick(episode) },
                focusRequester = if (isFirst) gridFocusRequester else null
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeGridCard(
    episode: Episode,
    isCurrent: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = FocusDefaults.cardScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceVariant,
            focusedContainerColor = SurfaceFocus,
            contentColor = OnSurface,
            focusedContentColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(Shapes.card),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCurrent) BrandBlue else SurfaceFocus
                )
            ),
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    FocusDefaults.borderWidthThick,
                    Color.White
                )
            )
        ),
        modifier = Modifier.then(
            if (focusRequester != null) Modifier.focusRequester(focusRequester)
            else Modifier
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(Shapes.card)
                    .background(
                        Brush.verticalGradient(listOf(SurfaceFocus, Background))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Scrim, Shapes.badge)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "E${episode.number}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(BrandBlue, Shapes.badge)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "ТРИВАЄ",
                            color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Text(
                text = if (episode.title.isNotBlank() && episode.title != episode.number.toString()) {
                    episode.title
                } else {
                    "Серія ${episode.number}"
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) BrandBlue else OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
