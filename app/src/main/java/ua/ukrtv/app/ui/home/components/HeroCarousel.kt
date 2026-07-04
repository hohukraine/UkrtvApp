package ua.ukrtv.app.ui.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.Gold
import ua.ukrtv.app.ui.theme.HeroDefaults
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.OnSurfaceVariant
import ua.ukrtv.app.ui.theme.OverlayLight
import ua.ukrtv.app.ui.theme.Shapes

private const val AUTO_SCROLL_INTERVAL_MS = 4000L

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    items: List<Movie>,
    brandColor: Color,
    onWatchClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val coroutineScope = rememberCoroutineScope()
    var isFocused by remember { mutableStateOf(false) }

    val currentAccentColor by animateColorAsState(
        targetValue = items[pagerState.currentPage].brandColor?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { brandColor }
        } ?: brandColor,
        animationSpec = tween(400),
        label = "heroAccent"
    )

    LaunchedEffect(pagerState.isScrollInProgress, isFocused) {
        while (isActive) {
            if (!pagerState.isScrollInProgress && !isFocused) {
                delay(AUTO_SCROLL_INTERVAL_MS)
                val next = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(next)
            } else {
                delay(500)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HeroDefaults.height)
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = HeroDefaults.horizontalPadding * 2),
            pageSpacing = 24.dp,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val movie = items[page]

            Surface(
                onClick = { onWatchClick(movie) },
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged {
                        isFocused = it.isFocused
                        if (it.isFocused) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(page)
                            }
                        }
                    },
                shape = ClickableSurfaceDefaults.shape(Shapes.card),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 0.dp,
                            color = Color.Transparent
                        ),
                        shape = Shapes.card
                    ),
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            color = currentAccentColor.copy(alpha = 0.8f)
                        ),
                        shape = Shapes.card
                    )
                ),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = OverlayLight,
                    focusedContainerColor = OverlayLight
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
            ) {
                HeroItemContent(item = movie, accentColor = currentAccentColor)
            }
        }

        PageIndicator(
            pageCount = items.size,
            currentPage = pagerState.currentPage,
            brandColor = currentAccentColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroItemContent(
    item: Movie,
    accentColor: Color
) {
    val context = LocalContext.current
    val posterRequest = remember(item.poster) {
        ImageRequest.Builder(context)
            .data(item.poster)
            .size(360, 480)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.5f),
                        accentColor.copy(alpha = 0.15f),
                        Background
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .fillMaxHeight()
                    .clip(Shapes.cardCompact)
                    .background(OverlayLight)
            ) {
                AsyncImage(
                    model = posterRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title.uppercase(),
                    color = OnSurface,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 4.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    if (!item.rating.isNullOrEmpty()) {
                        Surface(
                            colors = ClickableSurfaceDefaults.colors(containerColor = Gold),
                            shape = ClickableSurfaceDefaults.shape(Shapes.badge),
                            onClick = {}
                        ) {
                            Text(
                                text = "IMDb ${item.rating}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text =                         "2026 • UA",
                        color = OnSurfaceVariant,
                        fontSize = 14.sp
                    )
                }

                Surface(
                    onClick = { /* Internal */ },
                    shape = ClickableSurfaceDefaults.shape(Shapes.button),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
                ) {
                    Text(
                        text = "ДИВИТИСЯ ЗАРАЗ",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    brandColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val size by animateDpAsState(
                targetValue = if (isSelected) 8.dp else 5.dp,
                animationSpec = tween(300),
                label = "indicatorSize"
            )
            val dotColor by animateColorAsState(
                targetValue = if (isSelected) brandColor else Color.White.copy(alpha = 0.2f),
                animationSpec = tween(300),
                label = "indicatorColor"
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
