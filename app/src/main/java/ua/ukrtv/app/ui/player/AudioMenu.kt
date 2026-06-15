package ua.ukrtv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.compose.material3.Text
import androidx.tv.material3.Button

@Composable
fun AudioMenu(
    player: Player,
    onDismiss: () -> Unit
) {
    val audioTracks = remember { mutableStateListOf<String>() }
    var selectedTrack by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        // Initialize audio tracks
        try {
            val windowCount = player.currentTimeline.windowCount
            for (i in 0 until windowCount) {
                audioTracks.add(if (i == 0) "Джерело 1" else "Джерело ${i + 1}")
            }
        } catch (e: Exception) {
            audioTracks.add("Джерело 1")
        }
    }
    
    Column(
        modifier = Modifier
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        Text(
            text = "Озвучка",
            color = Color(0xFFEEEEEE),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        LazyColumn {
            itemsIndexed(audioTracks) { index, track ->
                Button(
                    onClick = {
                        selectedTrack = index
                        onDismiss()
                    },
                    content = {
                        Text(
                            text = if (index == selectedTrack) "✓ $track" else track,
                            color = if (index == selectedTrack) Color(0xFFFFD500) else Color(0xFFEEEEEE)
                        )
                    }
                )
            }
        }
    }
}