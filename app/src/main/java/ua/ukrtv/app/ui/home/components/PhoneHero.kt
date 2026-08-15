package ua.ukrtv.app.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import coil3.request.ImageRequest
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.ui.theme.Gold
import ua.ukrtv.app.ui.theme.Background as AppBackground
import kotlinx.coroutines.delay

private const val HERO_HEIGHT_FRACTION = 0.70f
private const val AUTO_SCROLL_INTERVAL_MS = 4000L

@Composable
fun PhoneHeroSection(
    items: List<Top200Movie>,
    brandColor: Color,
    onItemClick: (Top200Movie) -> Unit,
    onActiveMovieChange: (Top200Movie) -> Unit,
    scrollFraction: () -> Float,
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
                val f = scrollFraction()
                translationY = -f * h * 0.15f
                alpha = (1f - f).coerceIn(0f, 1f)
            }
            // Track user interaction to pause auto-scroll WITHOUT consuming pointer events.
            // A plain detectDragGestures here would consume vertical drags and block the
            // parent LazyColumn from scrolling over the hero (70% of the screen).
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isUserInteracting = true
                    try {
                        while (true) {
                            if (awaitPointerEvent().type == PointerEventType.Release) break
                        }
                    } finally {
                        isUserInteracting = false
                    }
                }
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
                val dotScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.6f else 1f,
                    animationSpec = tween(300),
                    label = "dotScale"
                )
                val dotAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.3f,
                    animationSpec = tween(300),
                    label = "dotAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .graphicsLayer {
                            scaleX = dotScale
                            scaleY = dotScale
                            alpha = dotAlpha
                        }
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) brandColor else Color.White)
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
    val context = LocalContext.current
    val accentColor = remember(movie.accentColor) {
        try { Color(android.graphics.Color.parseColor(movie.accentColor)) } catch (_: Exception) { Color(0xFF08121c) }
    }
    val onAccentColor = remember(movie.onAccentColor) {
        try { Color(android.graphics.Color.parseColor(movie.onAccentColor)) } catch (_: Exception) { Color.White }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onClick() }
    ) {
        // Backdrop Image (Always visible)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(movie.backdropUrl)
                .crossfade(600)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.85f }
        )

        // Dynamic Gradient based on the movie's actual color
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.3f),
                            accentColor.copy(alpha = 0.6f),
                            ua.ukrtv.app.ui.theme.Background
                        ),
                        startY = 0f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Poster with adaptive border
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.6f)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141414))
                    .border(
                        width = 1.dp,
                        color = onAccentColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .shadow(20.dp, RoundedCornerShape(12.dp))
            ) {
                val imageRequest = remember(movie.posterUrl) {
                    ImageRequest.Builder(context)
                        .data(movie.posterUrl)
                        .size(480, 720)
                        .crossfade(100)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Rank badge
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

            Spacer(modifier = Modifier.height(16.dp))

            // Adaptive Title (Uses contrast color from model)
            Text(
                text = movie.title,
                color = onAccentColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata row (Uses contrast color)
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
                        color = onAccentColor.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
                if (movie.genres.isNotEmpty()) {
                    Text(
                        movie.genres.take(2).joinToString(" \u00B7 "),
                        color = onAccentColor.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
