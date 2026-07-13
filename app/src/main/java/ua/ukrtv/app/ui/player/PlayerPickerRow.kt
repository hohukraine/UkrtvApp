package ua.ukrtv.app.ui.player

import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerPickerRow(
    columns: List<PickerColumn>,
    focusedIndex: Int,
    brandColor: Color,
    onColumnFocused: (Int) -> Unit,
    onValueChange: (direction: Int) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        columns.forEachIndexed { index, column ->
            val isFocused = index == focusedIndex

            Surface(
                onClick = {
                    onColumnFocused(index)
                    if (column.needsCommit) onCommit()
                },
                enabled = column.enabled,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isFocused) brandColor.copy(alpha = 0.2f) else Color.Transparent,
                    focusedContainerColor = brandColor.copy(alpha = 0.25f),
                    contentColor = Color.Transparent,
                    focusedContentColor = Color.Transparent
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(2.dp, brandColor.copy(alpha = 0.6f))
                    )
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier
                    .widthIn(max = 140.dp)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) onColumnFocused(index)
                    }
                    .onKeyEvent { event ->
                        if (!column.enabled) return@onKeyEvent false
                        val ke = event.nativeKeyEvent
                        when (ke.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (ke.action == KeyEvent.ACTION_UP) onValueChange(1)
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (ke.action == KeyEvent.ACTION_UP) onValueChange(-1)
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = column.label,
                        color = if (isFocused) brandColor else Color(0xFF999999),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = column.value,
                        color = if (isFocused) Color.White else Color(0xFFE1E1E1).copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
