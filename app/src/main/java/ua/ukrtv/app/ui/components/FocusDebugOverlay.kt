package ua.ukrtv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * Toggleable on-screen focus diagnostics HUD (for QA on a real TV / emulator).
 *
 * Press the remote's MENU key (repeat press) to toggle the overlay. The HUD itself observes
 * whether its sibling composition reports an active focus via [LocalFocusManager], so you can
 * quickly detect "focus lost in space" bugs — when the D-pad drops focus to nothing because no
 * focusable target exists below the hero or at a row boundary.
 *
 * No-op on phone / tablet form factors (touch-driven, no D-pad focus concern).
 */
@Composable
fun FocusDebugOverlay(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled) return

    var visible by remember { mutableStateOf(false) }
    // Reading the focus manager forces this node to observe the shared focus owner, and
    // coupling a local bool to it gives a cheap "is anything focused" signal for QA.
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.Menu || event.key == Key.Settings || event.key == Key.NumPadMultiply) {
                    visible = !visible
                    true
                } else false
            }
    ) {
        if (visible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "FOCUS DEBUG",
                        color = Color(0xFFFFD700),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "focusManager present: YES",
                        color = Color(0xFF7CFC00),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "MENU: toggle overlay",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
