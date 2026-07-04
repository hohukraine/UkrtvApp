package ua.ukrtv.app.ui.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.Gold
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.OnSurfaceVariant
import ua.ukrtv.app.ui.theme.OverlayLight
import ua.ukrtv.app.ui.theme.SurfaceFocus
import ua.ukrtv.app.domain.model.Top200Movie

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Top200HeroCarousel(
    items: List<Top200Movie>,
    brandColor: Color,
    onItemClick: (Top200Movie) -> Unit,
    onItemLongClick: (Top200Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 100.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val movie = items[page]

            Surface(
                onClick = { onItemClick(movie) },
                onLongClick = { onItemLongClick(movie) },
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged {
                        if (it.isFocused) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(page)
                            }
                        }
                    },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 0.dp,
                            color = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ),
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 4.dp,
                            color = Gold
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                ),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = OverlayLight,
                    focusedContainerColor = OverlayLight
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
            ) {
                Top200HeroItemContent(item = movie)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Top200HeroItemContent(
    item: Top200Movie
) {
    val context = LocalContext.current
    
    // Attempting to find a poster URL. For now using a placeholder or trying to guess.
    // In a real app, we'd have these URLs pre-populated.
    val posterUrl = "https://img.youtube.com/vi/MNhYj67QDAw/maxresdefault.jpg" // Placeholder fallback

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Gold.copy(alpha = 0.15f),
                        Background
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster on the left
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceFocus)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.posterUrl.ifEmpty { posterUrl })
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Gold Rank Badge
                Surface(
                    modifier = Modifier.padding(8.dp),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Gold),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                    onClick = {}
                ) {
                    Text(
                        text = "#${item.rank}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Info on the right
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    color = OnSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = item.originalTitle,
                    color = OnSurfaceVariant,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = item.comment,
                    color = OnSurface.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { /* Internal */ },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Gold,
                            contentColor = OnSurface
                        )
                    ) {
                        Text(
                            text = "ДИВИТИСЯ",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text =                         "За версією Що подивитися",
                    color = OnSurfaceVariant,
                    fontSize = 14.sp
                    )
                }
            }
        }
    }
}
