package ua.ukrtv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults

@Composable
fun SearchBar(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 48.dp, vertical = 16.dp),
        shape = ButtonDefaults.shape(RoundedCornerShape(28.dp)),
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF1A1A1A)
        ),
        content = {
            Text(
                text = "Пошук",
                color = Color(0xFF888888)
            )
        }
    )
}