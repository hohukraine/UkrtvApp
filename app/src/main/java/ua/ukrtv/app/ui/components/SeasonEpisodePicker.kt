package ua.ukrtv.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import ua.ukrtv.app.domain.model.Season

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonEpisodePicker(
    seasons: List<Season>,
    onEpisodeClick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSeasonNum by remember { mutableStateOf(seasons.firstOrNull()?.number ?: 1) }
    val selectedSeason = seasons.find { it.number == selectedSeasonNum }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "СЕЗОНИ",
            color = Color(0xFF7A7A7A), // textMutedMinimal
            fontSize = 13.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(seasons) { season ->
                var isFocused by remember { mutableStateOf(false) }
                val isSelected = selectedSeasonNum == season.number

                Surface(
                    onClick = { selectedSeasonNum = season.number },
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) Color(0xFF151517) else Color.Transparent,
                        focusedContainerColor = Color(0xFFE1E1E1),
                        contentColor = if (isSelected) Color(0xFF6E85B7) else Color(0xFFE1E1E1),
                        focusedContentColor = Color(0xFF0C0C0D)
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    border = ClickableSurfaceDefaults.border(
                        border = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (isSelected) Color(0xFF6E85B7).copy(alpha = 0.5f) else Color.Transparent
                            )
                        ),
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF6E85B7))
                        )
                    ),
                    modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
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

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 70.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
        ) {
            selectedSeason?.episodes?.let { episodes ->
                items(episodes) { episode ->
                    var isFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { onEpisodeClick(selectedSeasonNum, episode.number) },
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFF151517),
                            focusedContainerColor = Color(0xFFE1E1E1),
                            contentColor = Color(0xFFE1E1E1),
                            focusedContentColor = Color(0xFF0C0C0D)
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF6E85B7))
                            )
                        ),
                        modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "E${episode.number}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
