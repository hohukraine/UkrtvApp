package ua.ukrtv.app.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil3.compose.AsyncImage
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.Montserrat
import ua.ukrtv.app.ui.theme.YouTv
import ua.ukrtv.app.util.DeviceClass

/**
 * YouTV hero overlay (widget_content_info.xml):
 * title block at 16% of screen height / 60% width, watch button 216x56 at 66% (bias 0.8),
 * dots indicator above the bottom row strip.
 */

@Composable
fun YouTvTop200Hero(
    items: List<Top200Movie>,
    brandColor: Color,
    onItemClick: (Top200Movie) -> Unit,
    onActiveMovieChange: ((Top200Movie) -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val deviceClass = LocalDeviceClass.current
    HeroPagerContainer(items = items.size, modifier = modifier, onActivePage = { onActiveMovieChange?.invoke(items[it]) }) { page ->
        val movie = items[page]
        if (deviceClass == DeviceClass.LOW) {
            LiteHeroBanner(
                title = movie.title,
                pillText = "ТОП ${movie.rank}",
                metaParts = buildList {
                    if (movie.year.isNotBlank()) add(movie.year)
                    addAll(movie.genres.take(2))
                },
                description = movie.comment,
                imageUrl = movie.posterUrl.ifBlank { movie.backdropUrl },
                brandColor = brandColor,
                onClick = { onItemClick(movie) }
            )
        } else {
            YouTvHeroOverlay(
                title = movie.title,
                pillText = "ТОП ${movie.rank}",
                metaParts = buildList {
                    if (movie.year.isNotBlank()) add(movie.year)
                    addAll(movie.genres.take(2))
                },
                description = movie.comment,
                brandColor = brandColor,
                onClick = { onItemClick(movie) }
            )
        }
    }
}

@Composable
fun YouTvMovieHero(
    items: List<Movie>,
    brandColor: Color,
    onWatchClick: (Movie) -> Unit,
    onActiveColorChange: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val deviceClass = LocalDeviceClass.current
    HeroPagerContainer(items = items.size, modifier = modifier, onActivePage = { page ->
        val m = items[page]
        val parsed = m.brandColor?.let { hex ->
            runCatching { android.graphics.Color.parseColor(hex) }.getOrNull()
        }
        if (parsed != null) onActiveColorChange?.invoke(parsed)
    }) { page ->
        val movie = items[page]
        if (deviceClass == DeviceClass.LOW) {
            LiteHeroBanner(
                title = movie.title,
                pillText = movie.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { "IMDb $it" },
                metaParts = buildList {
                    movie.year?.let { add(it.toString()) }
                    addAll(movie.genres.take(2))
                },
                description = movie.description,
                imageUrl = movie.poster,
                brandColor = brandColor,
                onClick = { onWatchClick(movie) }
            )
        } else {
            YouTvHeroOverlay(
                title = movie.title,
                pillText = movie.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { "IMDb $it" },
                metaParts = buildList {
                    movie.year?.let { add(it.toString()) }
                    addAll(movie.genres.take(2))
                },
                description = movie.description,
                brandColor = brandColor,
                onClick = { onWatchClick(movie) }
            )
        }
    }
}

private const val ACCENT_FALLBACK_HEX = 0xFF08121C
private val DEFAULT_ACCENT = Color(ACCENT_FALLBACK_HEX)

private fun parseAccent(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(DEFAULT_ACCENT)

/**
 * "Швидкість" preset banner: no fullscreen backdrop processing — the page-wide accent
 * gradient comes from HomeBackground; here we lay out poster card + text + button.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiteHeroBanner(
    title: String,
    pillText: String?,
    metaParts: List<String>,
    description: String?,
    imageUrl: String?,
    brandColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = YouTv.startLine, end = 96.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f)) {
                androidx.tv.material3.Text(
                    text = title,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Montserrat,
                    lineHeight = 38.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    pillText?.let { pill ->
                        Box(
                            modifier = Modifier
                                .background(brandColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            androidx.tv.material3.Text(
                                text = pill,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = Montserrat
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    val meta = metaParts.joinToString(" • ")
                    if (meta.isNotBlank()) {
                        androidx.tv.material3.Text(
                            text = meta,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontFamily = Montserrat,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    androidx.tv.material3.Text(
                        text = description.trim(),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontFamily = Montserrat,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(20.dp))
                HeroWatchButton(brandColor = brandColor, onClick = onClick)
            }

            if (!imageUrl.isNullOrBlank()) {
                Spacer(Modifier.width(32.dp))
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(220.dp)
                        .aspectRatio(120f / 164f)
                        .clip(RoundedCornerShape(YouTv.cardRadius))
                )
            }
        }
    }
}

@Composable
private fun HeroPagerContainer(
    items: Int,
    modifier: Modifier = Modifier,
    onActivePage: (Int) -> Unit,
    pageContent: @Composable (Int) -> Unit
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { items })
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) { onActivePage(pagerState.currentPage) }

    Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page -> pageContent(page) }

        // Dots indicator centered above the bottom grid strip (WidgetBannersDots).
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        ) {
            repeat(items) { i ->
                val active = pagerState.currentPage == i
                Box(
                    modifier = Modifier
                        .size(width = 8.dp, height = 8.dp)
                        .background(
                            if (active) Color.White else Color.White.copy(alpha = 0.35f),
                            CircleShape
                        )
                )
            }
        }
    }
}

/**
 * Info panel shown while a grid row has focus (YouTV keeps WidgetContentInfo visible and
 * repoints it at the focused card): title + pill/meta + description, no watch button.
 */
@Composable
fun YouTvFocusInfoPanel(
    movie: Movie,
    brandColor: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .offset(y = maxHeight * YouTv.HERO_INFO_TOP_PERCENT)
                .padding(start = YouTv.startLine, end = 96.dp)
                .width(maxWidth * 0.6f)
        ) {
            androidx.tv.material3.Text(
                text = movie.title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Montserrat,
                lineHeight = 38.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pill = movie.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { "IMDb $it" }
                    ?: if (movie.contentType?.contains("сер", ignoreCase = true) == true) "СЕРІАЛ" else "ФІЛЬМ"
                Box(
                    modifier = Modifier
                        .background(brandColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    androidx.tv.material3.Text(
                        text = pill,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = Montserrat
                    )
                }
                Spacer(Modifier.width(12.dp))
                val meta = buildList {
                    movie.year?.let { add(it.toString()) }
                    addAll(movie.genres.take(2))
                }.joinToString(" • ")
                if (meta.isNotBlank()) {
                    androidx.tv.material3.Text(
                        text = meta,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = Montserrat,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            movie.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(10.dp))
                androidx.tv.material3.Text(
                    text = desc.trim(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = Montserrat,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun YouTvHeroOverlay(
    title: String,
    pillText: String?,
    metaParts: List<String>,
    description: String?,
    brandColor: Color,
    onClick: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val h = maxHeight

        // Info block — top guideline at 16%, width 60% of screen.
        Column(
            modifier = Modifier
                .offset(y = h * YouTv.HERO_INFO_TOP_PERCENT)
                .padding(start = YouTv.startLine, end = 96.dp)
                .width(maxWidth * 0.6f)
        ) {
            androidx.tv.material3.Text(
                text = title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Montserrat,
                lineHeight = 38.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                pillText?.let { pill ->
                    Box(
                        modifier = Modifier
                            .background(brandColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                    ) {
                        androidx.tv.material3.Text(
                            text = pill,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = Montserrat
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                val meta = metaParts.joinToString(" • ")
                if (meta.isNotBlank()) {
                    androidx.tv.material3.Text(
                        text = meta,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = Montserrat,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Blogger's note / synopsis — Netflix-style short paragraph under the meta line.
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                androidx.tv.material3.Text(
                    text = description.trim(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = Montserrat,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Watch button — vertical guideline 66% with bias 0.8.
        val buttonTop = h * YouTv.HERO_BTN_TOP_PERCENT +
            (h * (1f - YouTv.HERO_BTN_TOP_PERCENT) - YouTv.heroBtnH) * YouTv.HERO_BTN_BIAS
        HeroWatchButton(
            brandColor = brandColor,
            onClick = onClick,
            modifier = Modifier.offset(y = buttonTop).padding(start = YouTv.startLine)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroWatchButton(
    brandColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(YouTv.buttonRadius)

    // bg_button_selector: dark translucent pill, brand fill + white content when focused.
    androidx.tv.material3.Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color(0x66000000),
            focusedContainerColor = brandColor,
            contentColor = Color.White,
            focusedContentColor = Color.White
        ),
        modifier = modifier
            .size(width = YouTv.heroBtnW, height = YouTv.heroBtnH)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xB3FFFFFF),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            androidx.tv.material3.Text(
                text = "ДИВИТИСЯ",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Montserrat
            )
        }
    }
}
