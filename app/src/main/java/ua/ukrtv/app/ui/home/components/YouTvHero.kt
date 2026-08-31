package ua.ukrtv.app.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.Montserrat
import ua.ukrtv.app.ui.theme.PosterStyle
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
    modifier: Modifier = Modifier,
    watchButtonFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    if (items.isEmpty()) return
    val deviceClass = LocalDeviceClass.current
    HeroPagerContainer(items = items.size, modifier = modifier, onActivePage = { onActiveMovieChange?.invoke(items[it]) }) { page ->
        val movie = items[page]
        val posterStyle = if (movie.backdropUrl.isNotBlank()) PosterStyle.WIDE else PosterStyle.VERTICAL
        if (deviceClass == DeviceClass.LOW) {
            LiteHeroBanner(
                title = movie.title,
                pillText = "ТОП ${movie.rank}",
                metaParts = buildList {
                    if (movie.year.isNotBlank()) add(movie.year)
                    addAll(movie.genres.take(2))
                },
                description = movie.comment,
                imageUrl = movie.backdropUrl.ifBlank { movie.posterUrl },
                brandColor = brandColor,
                posterStyle = posterStyle,
                onClick = { onItemClick(movie) },
                watchButtonFocusRequester = watchButtonFocusRequester
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
                onClick = { onItemClick(movie) },
                watchButtonFocusRequester = watchButtonFocusRequester
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
    modifier: Modifier = Modifier,
    watchButtonFocusRequester: FocusRequester = remember { FocusRequester() }
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
        val posterStyle = PosterStyle.forProvider(movie.provider)
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
                posterStyle = posterStyle,
                onClick = { onWatchClick(movie) },
                watchButtonFocusRequester = watchButtonFocusRequester
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
                onClick = { onWatchClick(movie) },
                watchButtonFocusRequester = watchButtonFocusRequester
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
    posterStyle: PosterStyle = PosterStyle.VERTICAL,
    onClick: () -> Unit,
    watchButtonFocusRequester: FocusRequester
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
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Montserrat,
                    lineHeight = 40.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    pillText?.let { pill ->
                        val isTop200 = pill.startsWith("ТОП")
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isTop200) Color(0xFFFFD700) else brandColor, 
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            androidx.tv.material3.Text(
                                text = pill,
                                color = if (isTop200) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = Montserrat,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    val meta = metaParts.joinToString(" \u2022 ")
                    if (meta.isNotBlank()) {
                        androidx.tv.material3.Text(
                            text = meta,
                            color = Color.White.copy(alpha = 0.75f),
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
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontFamily = Montserrat,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(20.dp))
                HeroWatchButton(brandColor = brandColor, onClick = onClick, focusRequester = watchButtonFocusRequester)
            }

            if (!imageUrl.isNullOrBlank()) {
                Spacer(Modifier.width(32.dp))
                val posterAspect = when (posterStyle) {
                    PosterStyle.WIDE -> 16f / 9f
                    PosterStyle.VERTICAL -> 120f / 164f
                }
                val posterWidth = when (posterStyle) {
                    PosterStyle.WIDE -> 300.dp
                    PosterStyle.VERTICAL -> 220.dp
                }
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(posterWidth)
                        .aspectRatio(posterAspect)
                        .clip(RoundedCornerShape(YouTv.cardRadius))
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun HeroPagerContainer(
    items: Int,
    modifier: Modifier = Modifier,
    onActivePage: (Int) -> Unit,
    pageContent: @Composable (Int) -> Unit
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { items })
    val scope = rememberCoroutineScope()
    var userNavigated by remember { mutableStateOf(false) }

    // Deterministic DPAD navigation. The pager composes its neighbors lazily, so relying on
    // native focus search into the previous page is flaky (LEFT used to "jump and bounce
    // back"). We consume LEFT/RIGHT, animate explicitly, then hand focus to the target page.
    // DOWN/UP are consumed too — the pager internally treats DPAD as horizontal scroll,
    // which steals vertical focus movement from us. We re-emit via FocusManager instead.
    val focusManager = LocalFocusManager.current
    val pageFocusRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }
    LaunchedEffect(pagerState.currentPage) {
        onActivePage(pagerState.currentPage)
        if (userNavigated) {
            withFrameNanos { }
            pageFocusRequesters[pagerState.currentPage]?.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        val target = pagerState.currentPage + 1
                        if (target < items) {
                            userNavigated = true
                            scope.launch { pagerState.animateScrollToPage(target) }
                            true
                        } else false
                    }
                    Key.DirectionLeft -> {
                        val target = pagerState.currentPage - 1
                        if (target >= 0) {
                            userNavigated = true
                            scope.launch { pagerState.animateScrollToPage(target) }
                            true
                        } else false
                    }
                    Key.DirectionDown -> {
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    }
                    Key.DirectionUp -> {
                        focusManager.moveFocus(FocusDirection.Up)
                        true
                    }
                    else -> false
                }
            }
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val requester = remember { FocusRequester() }
            SideEffect { pageFocusRequesters[page] = requester }
            DisposableEffect(page) {
                onDispose {
                    if (pageFocusRequesters[page] === requester) pageFocusRequesters.remove(page)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(requester)
            ) {
                pageContent(page)
            }
        }

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
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Montserrat,
                lineHeight = 40.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pill = movie.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { "IMDb $it" }
                    ?: if (movie.contentType?.contains("сер", ignoreCase = true) == true) "СЕРІАЛ" else "ФІЛЬМ"
                Box(
                    modifier = Modifier
                        .background(brandColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    androidx.tv.material3.Text(
                        text = pill,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Montserrat,
                        letterSpacing = 0.3.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                val meta = buildList {
                    movie.year?.let { add(it.toString()) }
                    addAll(movie.genres.take(2))
                }.joinToString(" \u2022 ")
                if (meta.isNotBlank()) {
                    androidx.tv.material3.Text(
                        text = meta,
                        color = Color.White.copy(alpha = 0.75f),
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
                    color = Color.White.copy(alpha = 0.7f),
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
    onClick: () -> Unit,
    watchButtonFocusRequester: FocusRequester
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
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Montserrat,
                lineHeight = 40.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                pillText?.let { pill ->
                    val isTop200 = pill.startsWith("ТОП")
                    Box(
                        modifier = Modifier
                            .background(
                                if (isTop200) Color(0xFFFFD700) else brandColor, 
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        androidx.tv.material3.Text(
                            text = pill,
                            color = if (isTop200) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = Montserrat,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                val meta = metaParts.joinToString(" \u2022 ")
                if (meta.isNotBlank()) {
                    androidx.tv.material3.Text(
                        text = meta,
                        color = Color.White.copy(alpha = 0.75f),
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
                    color = Color.White.copy(alpha = 0.7f),
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
            modifier = Modifier.offset(y = buttonTop).padding(start = YouTv.startLine),
            focusRequester = watchButtonFocusRequester
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroWatchButton(
    brandColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(YouTv.buttonRadius)

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "heroBtnScale"
    )

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
            .focusRequester(focusRequester)
            .size(width = YouTv.heroBtnW, height = YouTv.heroBtnH)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                if (isFocused) {
                    shadowElevation = 8.dp.toPx()
                }
            }
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
