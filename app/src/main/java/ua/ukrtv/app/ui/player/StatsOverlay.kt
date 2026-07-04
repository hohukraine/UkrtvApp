package ua.ukrtv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Format
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

@Composable
fun StatsOverlay(player: ExoPlayer, modifier: Modifier = Modifier) {
    var videoFormat by remember { mutableStateOf<Format?>(null) }
    var audioFormat by remember { mutableStateOf<Format?>(null) }
    var droppedFrames by remember { mutableIntStateOf(0) }
    var skippedFrames by remember { mutableIntStateOf(0) }
    var renderedFrames by remember { mutableIntStateOf(0) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player) {
        while (true) {
            videoFormat = player.videoFormat
            audioFormat = player.audioFormat
            val counters = player.videoDecoderCounters
            if (counters != null) {
                droppedFrames = counters.droppedBufferCount
                skippedFrames = counters.skippedOutputBufferCount
                renderedFrames = counters.renderedOutputBufferCount
            }
            bufferedPosition = player.bufferedPosition
            delay(1000)
        }
    }

    Box(
        modifier = modifier
            .padding(16.dp)
            .background(Color(0x80000000), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "СТАТИСТИКА ВІДЕО",
                color = Color(0xFF6E85B7),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))

            val vf = videoFormat
            if (vf != null) {
                InfoRow("Роздільна", "${vf.width}x${vf.height}")

                val mime = vf.sampleMimeType?.substringAfterLast("/")?.uppercase() ?: "N/A"
                val codecStr = vf.codecs
                val codecShort = codecStr?.substringBefore(".") ?: ""
                InfoRow("Формат", if (codecShort.isNotEmpty()) "$mime ($codecShort)" else mime)

                if (codecStr != null) InfoRow("Codec", codecStr)

                if (vf.bitrate > 0) {
                    val bitrateStr = if (vf.bitrate >= 1_000_000) {
                        "%.1f Мбіт/с".format(vf.bitrate / 1_000_000f)
                    } else {
                        "%.0f Кбіт/с".format(vf.bitrate / 1000f)
                    }
                    InfoRow("Бітрейт", bitrateStr)
                }

                if (vf.frameRate > 0) InfoRow("FPS", "%.2f".format(vf.frameRate))

                if (vf.pixelWidthHeightRatio != 1f) {
                    InfoRow("PAR", "%.2f".format(vf.pixelWidthHeightRatio))
                }
            } else {
                InfoRow("Роздільна", "очікування...")
            }

            Spacer(modifier = Modifier.height(6.dp))

            val af = audioFormat
            if (af != null) {
                val audioMime = af.sampleMimeType?.substringAfterLast("/")?.uppercase() ?: "N/A"
                InfoRow("Аудіо", audioMime)
                if (af.sampleRate > 0) InfoRow("Частота", "${af.sampleRate} Гц")
                if (af.channelCount > 0) InfoRow("Канали", af.channelCount.toString())
            }

            Spacer(modifier = Modifier.height(6.dp))

            InfoRow("Відтворено", renderedFrames.toString())
            InfoRow("Пропущено", droppedFrames.toString())
            if (skippedFrames > 0) InfoRow("Пропущено(скіп)", skippedFrames.toString())
            InfoRow("Буфер", "${bufferedPosition / 1000}с")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
