package ua.ukrtv.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Season

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonEpisodePicker(
    seasons: List<Season>,
    onEpisodeClick: (Int, Episode, String?) -> Unit,
    accentColor: Color = Color(0xFF6E85B7),
    modifier: Modifier = Modifier
) {
    val distinctSeasons = remember(seasons) { seasons.distinctBy { it.number }.sortedBy { it.number } }
    
    val seasonKey = remember(distinctSeasons) { distinctSeasons.map { it.number }.toList() }
    var selectedSeasonNum by remember(seasonKey) {
        mutableStateOf(distinctSeasons.firstOrNull()?.number ?: 1)
    }
    val selectedSeason = distinctSeasons.find { it.number == selectedSeasonNum }

    val initialVo = remember(seasonKey) { selectedSeason?.voiceoverOptions?.firstOrNull() }
    var selectedVoiceover by remember(seasonKey) { mutableStateOf(initialVo) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Season selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            Text(
                text = "СЕЗОНИ",
                color = Color(0xFF7A7A7A),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.width(24.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(distinctSeasons, key = { it.number }) { season ->
                    val isSelected = selectedSeasonNum == season.number
                    Surface(
                        onClick = {
                            selectedSeasonNum = season.number
                            selectedVoiceover = season.voiceoverOptions.firstOrNull()
                        },
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) accentColor else Color(0xFF1A1A1D),
                            focusedContainerColor = accentColor,
                            contentColor = if (isSelected) Color.White else Color(0xFF9A9A9A),
                            focusedContentColor = Color.White
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        border = ClickableSurfaceDefaults.border(
                            border = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) accentColor else Color(0xFF2A2A2D)
                                )
                            ),
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, accentColor)
                            )
                        ),
                        modifier = Modifier.onFocusChanged { focused ->
                            if (focused.isFocused) {
                                selectedSeasonNum = season.number
                                selectedVoiceover = season.voiceoverOptions.firstOrNull()
                            }
                        }
                    ) {
                        Text(
                            text = "Сезон ${season.number}",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Voiceover selector
        if (selectedSeason?.voiceoverOptions?.size ?: 0 > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(selectedSeason!!.voiceoverOptions, key = { it }) { voiceover ->
                    val isSelected = selectedVoiceover == voiceover
                    Surface(
                        onClick = { selectedVoiceover = voiceover },
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) accentColor.copy(alpha = 0.3f) else Color(0xFF151517),
                            focusedContainerColor = accentColor.copy(alpha = 0.3f),
                            contentColor = if (isSelected) accentColor else Color(0xFF9A9A9A),
                            focusedContentColor = accentColor
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        border = ClickableSurfaceDefaults.border(
                            border = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) accentColor.copy(alpha = 0.6f) else Color(0xFF2A2A2D)
                                )
                            ),
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, accentColor.copy(alpha = 0.6f))
                            )
                        ),
                        modifier = Modifier.onFocusChanged { focused ->
                            if (focused.isFocused) selectedVoiceover = voiceover
                        }
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
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Episode list (horizontal)
        val episodes = selectedSeason?.voiceovers?.find { it.name == selectedVoiceover }?.episodes
            ?: selectedSeason?.episodes ?: emptyList()
        val distinctEpisodes = remember(episodes) { episodes.distinctBy { it.number }.sortedBy { it.number } }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(distinctEpisodes, key = { it.number }) { episode ->
                EpisodeCard(
                    episode = episode,
                    seasonNumber = selectedSeasonNum,
                    accentColor = accentColor,
                    onClick = { onEpisodeClick(selectedSeasonNum, episode, selectedVoiceover) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeCard(
    episode: Episode,
    seasonNumber: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF151517),
            focusedContainerColor = Color(0xFF2A2A2D),
            contentColor = Color(0xFFE1E1E1),
            focusedContentColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A2D))
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, accentColor)
            )
        )
    ) {
        Column(
            modifier = Modifier.width(140.dp).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = episode.number.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val epTitle = if (episode.title.isNotBlank() && episode.title != episode.number.toString()) {
                episode.title
            } else {
                "Серія ${episode.number}"
            }
            Text(
                text = epTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE1E1E1),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
