package ua.ukrtv.app.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import kotlinx.coroutines.delay
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.util.DeviceClass

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonEpisodePicker(
    seasons: List<Season>,
    onEpisodeClick: (Int, Episode, String?) -> Unit,
    accentColor: Color = Color(0xFF6E85B7),
    modifier: Modifier = Modifier
) {
    val deviceClass = LocalDeviceClass.current
    val distinctSeasons = remember(seasons) { seasons.distinctBy { it.number }.sortedBy { it.number } }
    val seasonKey = remember(distinctSeasons) { distinctSeasons.map { it.number }.toList() }

    var selectedSeasonNum by remember(seasonKey) {
        mutableStateOf(distinctSeasons.firstOrNull()?.number ?: 1)
    }
    val selectedSeason = distinctSeasons.find { it.number == selectedSeasonNum }

    val voiceoverOptions = remember(selectedSeason) {
        selectedSeason?.voiceoverOptions?.filter { it.isNotBlank() } ?: emptyList()
    }
    var selectedVoiceover by remember(voiceoverOptions) {
        mutableStateOf(voiceoverOptions.firstOrNull())
    }

    val episodes = remember(selectedSeason, selectedVoiceover) {
        if (selectedVoiceover != null && selectedSeason != null) {
            selectedSeason.voiceovers
                .find { it.name == selectedVoiceover }
                ?.episodes
                ?.distinctBy { it.number }
                ?.sortedBy { it.number }
                ?: selectedSeason.episodes.distinctBy { it.number }.sortedBy { it.number }
        } else {
            selectedSeason?.episodes?.distinctBy { it.number }?.sortedBy { it.number } ?: emptyList()
        }
    }

    var entranceTrigger by remember(seasonKey) { mutableStateOf(0L) }
    LaunchedEffect(seasonKey) {
        entranceTrigger = System.currentTimeMillis()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header: "ЕПІЗОДИ" + compact season pills
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ЕПІЗОДИ",
                color = accentColor.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(distinctSeasons, key = { _, s -> s.number }, contentType = { _, _ -> "season" }) { _, season ->
                    val isSelected = selectedSeasonNum == season.number
                    Surface(
                        onClick = { selectedSeasonNum = season.number },
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) accentColor else Color.Transparent,
                            focusedContainerColor = accentColor,
                            contentColor = if (isSelected) Color.White else accentColor.copy(alpha = 0.6f),
                            focusedContentColor = Color.White
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, if (isSelected) accentColor else accentColor.copy(alpha = 0.3f)
                                )
                            ),
                            focusedBorder = Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, accentColor)
                            )
                        ),
                        modifier = Modifier
                            .onFocusChanged { focused ->
                                if (focused.isFocused) selectedSeasonNum = season.number
                            }
                            .semantics { contentDescription = "Сезон ${season.number}" }
                    ) {
                        Text(
                            text = season.number.toString(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Voiceover pills
        if (voiceoverOptions.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                itemsIndexed(voiceoverOptions, key = { _, vo -> vo }, contentType = { _, _ -> "voiceover" }) { _, vo ->
                    val isSelected = selectedVoiceover == vo
                    Surface(
                        onClick = { selectedVoiceover = vo },
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent,
                            focusedContainerColor = accentColor.copy(alpha = 0.3f),
                            contentColor = if (isSelected) Color.White else accentColor.copy(alpha = 0.6f),
                            focusedContentColor = Color.White
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, if (isSelected) accentColor else accentColor.copy(alpha = 0.3f)
                                )
                            ),
                            focusedBorder = Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, accentColor)
                            )
                        ),
                        modifier = Modifier.semantics { contentDescription = vo }
                    ) {
                        Text(
                            text = vo,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Episode rail
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(episodes, key = { _, ep -> "${selectedSeasonNum}_${ep.number}" }, contentType = { _, _ -> "episode" }) { index, episode ->
                EpisodeCard(
                    episode = episode,
                    seasonNumber = selectedSeasonNum,
                    accentColor = accentColor,
                    index = index,
                    entranceTrigger = entranceTrigger,
                    deviceClass = deviceClass,
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
    index: Int,
    entranceTrigger: Long,
    deviceClass: DeviceClass,
    onClick: () -> Unit
) {
    val cardWidth = when (deviceClass) {
        DeviceClass.LOW -> 120.dp
        DeviceClass.MID -> 150.dp
        DeviceClass.HIGH -> 180.dp
    }
    val cardHeight = when (deviceClass) {
        DeviceClass.LOW -> 110.dp
        DeviceClass.MID -> 140.dp
        DeviceClass.HIGH -> 175.dp
    }

    val staggerMs = when (deviceClass) {
        DeviceClass.HIGH -> 40
        DeviceClass.MID -> 20
        DeviceClass.LOW -> 0
    }
    val animDuration = when (deviceClass) {
        DeviceClass.HIGH -> 250
        DeviceClass.MID -> 150
        DeviceClass.LOW -> 0
    }

    var itemVisible by remember(entranceTrigger, index) { mutableStateOf(deviceClass == DeviceClass.LOW) }
    LaunchedEffect(entranceTrigger, index) {
        if (deviceClass != DeviceClass.LOW) {
            delay((index * staggerMs).toLong())
            itemVisible = true
        }
    }

    val animAlpha by animateFloatAsState(
        targetValue = if (itemVisible) 1f else 0f,
        animationSpec = if (deviceClass == DeviceClass.LOW) {
            snap()
        } else {
            tween(durationMillis = animDuration)
        },
        label = "epAlpha"
    )

    val animScale by animateFloatAsState(
        targetValue = if (itemVisible) 1f else 0.95f,
        animationSpec = if (deviceClass == DeviceClass.HIGH) {
            spring(dampingRatio = 0.7f, stiffness = 300f)
        } else {
            snap()
        },
        label = "epScale"
    )

    val focusScale = when (deviceClass) {
        DeviceClass.LOW -> 1.05f
        DeviceClass.MID -> 1.05f
        DeviceClass.HIGH -> 1.08f
    }

    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF151517),
            focusedContainerColor = Color(0xFF1E1E21),
            contentColor = Color(0xFFE1E1E1),
            focusedContentColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A2D))
            ),
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    when (deviceClass) {
                        DeviceClass.LOW -> 1.dp
                        DeviceClass.MID -> 2.dp
                        DeviceClass.HIGH -> 2.dp
                    },
                    accentColor
                )
            )
        ),
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .then(
                if (deviceClass == DeviceClass.HIGH && itemVisible) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = accentColor.copy(alpha = 0.15f),
                        spotColor = accentColor.copy(alpha = 0.1f)
                    )
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp).then(
                Modifier.drawBehind {
                    val alphaVal = animAlpha
                    if (alphaVal < 1f) {
                        drawRect(color = Color(0xFF151517).copy(alpha = 1f - alphaVal))
                    }
                }
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Episode number — typography as hero
                val numSize = when (deviceClass) {
                    DeviceClass.LOW -> 28.sp
                    DeviceClass.MID -> 36.sp
                    DeviceClass.HIGH -> 44.sp
                }
                Text(
                    text = episode.number.toString().padStart(2, '0'),
                    fontSize = numSize,
                    fontWeight = if (deviceClass == DeviceClass.LOW) FontWeight.Bold else FontWeight.Light,
                    color = Color(0xFF7A7A7A),
                    modifier = Modifier.graphicsLayer {
                        alpha = animAlpha
                        scaleX = animScale
                        scaleY = animScale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
                )

                // Accent divider line (MID+)
                if (deviceClass >= DeviceClass.MID) {
                    val lineW = if (deviceClass == DeviceClass.MID) 30.dp else 40.dp
                    val lineH = if (deviceClass == DeviceClass.MID) 1.dp else 2.dp
                    Box(
                        modifier = Modifier
                            .width(lineW)
                            .height(lineH)
                            .background(accentColor.copy(alpha = 0.4f))
                            .graphicsLayer {
                                alpha = animAlpha
                                scaleX = animScale
                            }
                    )
                }

                // Title
                val epTitle = if (episode.title.isNotBlank() && episode.title != episode.number.toString()) {
                    episode.title
                } else {
                    "Епізод ${episode.number}"
                }
                val titleSize = when (deviceClass) {
                    DeviceClass.LOW -> 11.sp
                    DeviceClass.MID -> 12.sp
                    DeviceClass.HIGH -> 13.sp
                }
                Text(
                    text = epTitle,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE1E1E1),
                    maxLines = if (deviceClass == DeviceClass.LOW) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        alpha = animAlpha
                        scaleY = animScale
                    }
                )
            }
        }
    }
}
