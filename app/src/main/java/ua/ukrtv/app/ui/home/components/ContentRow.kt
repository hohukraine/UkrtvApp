package ua.ukrtv.app.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.home.CompactMovieCard
import ua.ukrtv.app.ui.home.ContinueWatchingCard
import ua.ukrtv.app.ui.home.MovieCard
import ua.ukrtv.app.ui.theme.GridDefaults

@Composable
fun ContentRow(
    title: String,
    items: List<Movie>,
    brandColor: Color,
    onItemClick: (Movie) -> Unit,
    onItemDismiss: ((Movie) -> Unit)? = null,
    useWideCards: Boolean = false
) {
    val titleColor = remember(brandColor) { brandColor.copy(alpha = 0.7f) }
    val rowT = remember { System.currentTimeMillis() }
    var focusedItemKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(items) {
        ua.ukrtv.app.util.AppLogger.d("ContentRow", "'$title' rendered ${items.size} items at ${System.currentTimeMillis() - rowT}ms")
    }

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        if (title.isNotEmpty()) {
            Text(
                text = title.uppercase(),
                color = titleColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(start = GridDefaults.horizontalPadding, bottom = 16.dp, top = 24.dp)
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = GridDefaults.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(GridDefaults.columnSpacing)
        ) {
            items(
                items = items,
                key = { it.pageUrl },
                contentType = { if (useWideCards) "wide" else "movie" }
            ) { item ->
                val isExpanded = focusedItemKey == item.pageUrl

                if (useWideCards) {
                    ContinueWatchingCard(
                        movie = item,
                        brandColor = brandColor,
                        onClick = { onItemClick(item) },
                        onDismiss = onItemDismiss?.let { { it(item) } },
                        modifier = Modifier.onFocusChanged { state ->
                            focusedItemKey = if (state.isFocused) item.pageUrl else null
                        }
                    )
                } else {
                    MovieCard(
                        movie = item,
                        brandColor = brandColor,
                        isExpanded = isExpanded,
                        onClick = { onItemClick(item) },
                        onDismiss = onItemDismiss?.let { { it(item) } },
                        modifier = Modifier.onFocusChanged { state ->
                            focusedItemKey = if (state.isFocused) item.pageUrl else null
                        }
                    )
                }
            }
        }
    }
}
