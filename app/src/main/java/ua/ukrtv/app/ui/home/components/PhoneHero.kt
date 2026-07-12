package ua.ukrtv.app.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.ui.theme.Gold
import kotlinx.coroutines.delay

private const val HERO_HEIGHT_FRACTION = 0.70f
private const val AUTO_SCROLL_INTERVAL_MS = 4000L

@Composable
fun PhoneHeroSection(
    items: List<Top200Movie>,
    brandColor: Color,
    onItemClick: (Top200Movie) -> Unit,
    onActiveMovieChange: (Top200Movie) -> Unit,
    scrollFraction: Float,
    screenHeightDp: Float,
) {
    if (items.isEmpty()) return

    val heroHeight = (screenHeightDp * HERO_HEIGHT_FRACTION).dp

    val pagerState = rememberPagerState(pageCount = { items.size })
    var isUserInteracting by remember { mutableStateOf(false) }

    val currentPage by remember {
        derivedStateOf { pagerState.currentPage.coerceIn(0, items.size - 1) }
    }
    LaunchedEffect(currentPage) {
        onActiveMovieChange(items[currentPage])
    }

    // Auto-scroll with pause on user interaction
    LaunchedEffect(pagerState, isUserInteracting) {
        if (!isUserInteracting && items.size > 1) {
            delay(AUTO_SCROLL_INTERVAL_MS)
            val nextPage = (pagerState.currentPage + 1) % items.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .graphicsLayer {
                val h = size.height
                translationY = -scrollFraction * h * 0.15f
                alpha = (1f - scrollFraction).coerceIn(0f, 1f)
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isUserInteracting = true },
                    onDragEnd = { isUserInteracting = false },
                    onDragCancel = { isUserInteracting = false },
                    onDrag = { _, _ -> }
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val movie = items[page]
            PhoneHeroPage(
                movie = movie,
                brandColor = brandColor,
                isActive = page == currentPage,
                onClick = { onItemClick(movie) }
            )
        }

        // Page indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, _ ->
                val isSelected = index == currentPage
                val dotSize by animateFloatAsState(
                    targetValue = if (isSelected) 8f else 5f,
                    animationSpec = tween(300),
                    label = "dotSize"
                )
                Box(
                    modifier = Modifier
                        .size(dotSize.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) brandColor
                            else Color.White.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

@Composable
private fun PhoneHeroPage(
    movie: Top200Movie,
    brandColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Poster — fills available height, aspect ratio 2:3
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141414))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(movie.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Rank badge — top-left on poster
                if (movie.rank > 0) {
                    Box(
                        modifier = Modifier
                            .padding(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "#${movie.rank}",
                            color = Gold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact metadata row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (movie.rating > 0) {
                    Text(
                        "\u2605 ${movie.rating}",
                        color = Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (movie.year.isNotEmpty()) {
                    Text(
                        movie.year,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
                if (movie.genres.isNotEmpty()) {
                    Text(
                        movie.genres.take(3).joinToString(" \u00B7 "),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (movie.director.isNotEmpty()) {
                    Text(
                        movie.director,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
