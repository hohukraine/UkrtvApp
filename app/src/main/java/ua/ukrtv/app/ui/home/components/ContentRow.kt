package ua.ukrtv.app.ui.home.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.home.MovieCard

@Composable
fun ContentRow(
    title: String,
    items: List<Movie>,
    brandColor: Color,
    onItemClick: (Movie) -> Unit,
    onItemDismiss: ((Movie) -> Unit)? = null
) {
    val titleColor = remember(brandColor) { brandColor.copy(alpha = 0.7f) }

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = title.uppercase(),
            color = titleColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(start = 58.dp, bottom = 16.dp, top = 24.dp)
        )

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 58.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items.forEachIndexed { index, item ->
                MovieCard(
                    movie = item,
                    onClick = { onItemClick(item) },
                    onDismiss = if (onItemDismiss != null) {{ onItemDismiss(item) }} else null,
                    loadDelayMs = index * 50
                )
            }
        }
    }
}
