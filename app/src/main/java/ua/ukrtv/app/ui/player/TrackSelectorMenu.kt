package ua.ukrtv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun TrackSelectorMenu(
    title: String,
    tracks: List<TrackInfo>,
    selectedTrackIndex: Int?,
    onTrackSelected: (TrackInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(end = 32.dp)
            .background(Color(0xE6151517), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = Color(0xFFE1E1E1),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))
            tracks.forEach { track ->
                val isSelected = track.trackIndex == selectedTrackIndex
                androidx.tv.material3.Surface(
                    onClick = { onTrackSelected(track) },
                    scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color(0xFF2A2A2D),
                        contentColor = if (isSelected) Color(0xFF6E85B7) else Color(0xFFE1E1E1),
                        focusedContentColor = Color.White
                    ),
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp))
                ) {
                    Text(
                        text = track.label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
