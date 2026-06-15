package ua.ukrtv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
fun SubtitleMenu(
    player: Player,
    onDismiss: () -> Unit
) {
    val subtitles = remember { 
        mutableStateListOf<String>().apply {
            add("Субтитри вимк")
            add("Українські")
        }
    }
    
    var selectedSubtitle by remember { mutableStateOf(0) }
    
    Column(
        modifier = Modifier
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        Text(
            text = "Субтитри",
            color = Color(0xFFEEEEEE),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        LazyColumn {
            itemsIndexed(subtitles) { index, subtitle ->
                Button(
                    onClick = {
                        selectedSubtitle = index
                        onDismiss()
                    },
                    content = {
                        Text(
                            text = if (index == selectedSubtitle) "✓ $subtitle" else subtitle,
                            color = if (index == selectedSubtitle) Color(0xFFFFD500) else Color(0xFFEEEEEE)
                        )
                    }
                )
            }
        }
    }
}