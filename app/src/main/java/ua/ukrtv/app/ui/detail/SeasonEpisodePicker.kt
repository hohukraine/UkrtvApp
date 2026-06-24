package ua.ukrtv.app.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.Episode

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonEpisodePicker(
    seasons: List<Season>,
    onEpisodeClick: (Int, Episode) -> Unit,
    accentColor: Color = Color(0xFF6E85B7),
    modifier: Modifier = Modifier
) {
    val seasonKey = remember(seasons) { seasons.map { it.number }.toList() }
    var selectedSeasonNum by remember(seasonKey) {
        mutableStateOf(seasons.firstOrNull()?.number ?: 1)
    }
    val selectedSeason = seasons.find { it.number == selectedSeasonNum }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "СЕЗОНИ",
            color = Color(0xFF7A7A7A),
            fontSize = 13.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(seasons, key = { it.number }) { season ->
                val isSelected = selectedSeasonNum == season.number
                val onSeasonClick = remember(season.number) { { selectedSeasonNum = season.number } }

                Surface(
                    onClick = onSeasonClick,
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) accentColor.copy(alpha = 0.15f) else Color.Transparent,
                        focusedContainerColor = accentColor,
                        contentColor = if (isSelected) accentColor else Color(0xFFE1E1E1),
                        focusedContentColor = Color(0xFF0C0C0D)
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    border = ClickableSurfaceDefaults.border(
                        border = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (isSelected) accentColor.copy(alpha = 0.5f) else Color.Transparent
                            )
                        ),
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, accentColor)
                        )
                    ),
                    modifier = Modifier.onFocusChanged { focused ->
                        if (focused.isFocused) selectedSeasonNum = season.number
                    }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Сезон ${season.number}",
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "СЕРІЇ",
            color = Color(0xFF7A7A7A),
            fontSize = 13.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            selectedSeason?.episodes?.forEach { episode ->
                val onEpClick = remember(selectedSeasonNum, episode.id) {
                    { onEpisodeClick(selectedSeasonNum, episode) }
                }
                Surface(
                    onClick = onEpClick,
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFF1A1A1D),
                            focusedContainerColor = accentColor,
                            contentColor = Color(0xFFE1E1E1),
                            focusedContentColor = Color(0xFF0C0C0D)
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        border = ClickableSurfaceDefaults.border(
                            border = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, Color(0xFF2A2A2D)
                                )
                            ),
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, accentColor)
                            )
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = episode.number.toString(),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                if (!episode.voiceover.isNullOrEmpty()) {
                                    Text(
                                        text = episode.voiceover,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Light,
                                        color = Color(0xFF7A7A7A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
