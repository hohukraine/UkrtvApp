package ua.ukrtv.app.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import ua.ukrtv.app.ui.theme.PlaceholderDark
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.Gold
import ua.ukrtv.app.ui.theme.HeroDefaults
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.ui.theme.deviceImage
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.util.DeviceClass

private const val AUTO_SCROLL_INTERVAL_MS = 4000L

@Composable
fun HeroCarousel(
    items: List<Movie>,
    brandColor: Color,
    onWatchClick: (Movie) -> Unit,
    onActiveColorChange: (Color) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(
        pageCount = { Int.MAX_VALUE },
        initialPage = Int.MAX_VALUE / 2
    )
    val actualPage = ((pagerState.currentPage % items.size) + items.size) % items.size
    val coroutineScope = rememberCoroutineScope()
    var isFocused by remember { mutableStateOf(false) }
    var focusedPage by remember { mutableStateOf(-1) }
    var pendingFocusPage by remember { mutableStateOf(-1) }

    val deviceClass = LocalDeviceClass.current
    val rawAccent = remember(pagerState.currentPage) {
        items[actualPage].brandColor?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { brandColor }
        } ?: brandColor
    }
    val currentAccentColor: Color = if (deviceClass == DeviceClass.LOW) {
        rawAccent
    } else {
        val animated by animateColorAsState(
            targetValue = rawAccent,
            animationSpec = tween(400),
            label = "heroAccent"
        )
        animated
    }

    LaunchedEffect(currentAccentColor, isFocused) {
        if (isFocused) {
            onActiveColorChange(currentAccentColor)
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress, isFocused) {
        while (isActive) {
            if (!pagerState.isScrollInProgress && !isFocused) {
                delay(AUTO_SCROLL_INTERVAL_MS)
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                delay(500)
            }
        }
    }

    val heroHeight = if (deviceClass == DeviceClass.HIGH) HeroDefaults.height + 60.dp else HeroDefaults.height

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                // Final vertical mask to fade EVERYTHING to transparent at the bottom
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White, Color.White, Color.Transparent),
                        startY = 0f,
                        endY = size.height
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = HeroDefaults.horizontalPadding * 2),
            pageSpacing = 24.dp,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val movie = items[page]
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(pendingFocusPage) {
                if (pendingFocusPage == page) {
                    withFrameNanos { }
                    focusRequester.requestFocus()
                    pendingFocusPage = -1
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .clickable { onWatchClick(movie) }
                    .onFocusChanged {
                        isFocused = it.isFocused
                        if (it.isFocused) {
                            focusedPage = page
                            coroutineScope.launch { pagerState.animateScrollToPage(page) }
                        } else if (focusedPage == page) {
                            focusedPage = -1
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        val ke = event.nativeKeyEvent
                        if (ke.action == android.view.KeyEvent.ACTION_DOWN) {
                            when (ke.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    coroutineScope.launch {
                                        pendingFocusPage = page + 1
                                        pagerState.animateScrollToPage(page + 1)
                                    }
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    coroutineScope.launch {
                                        pendingFocusPage = page - 1
                                        pagerState.animateScrollToPage(page - 1)
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                HeroItemContent(
                    item = movie,
                    accentColor = currentAccentColor,
                    deviceClass = deviceClass,
                    isFocused = focusedPage == page
                )
            }
        }

        PageIndicator(
            pageCount = items.size,
            currentPage = actualPage,
            brandColor = currentAccentColor,
            deviceClass = deviceClass,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun HeroItemContent(
    item: Movie,
    accentColor: Color,
    deviceClass: DeviceClass,
    isFocused: Boolean = false
) {
    val ctx = LocalContext.current
    val isMediatek = LocalIsMediatek.current
    val (iw, ih) = when (deviceClass) { DeviceClass.LOW -> 180 to 240; DeviceClass.MID -> 360 to 480; DeviceClass.HIGH -> 540 to 720 }
    val posterRequest = remember(item.poster, deviceClass) {
        ImageRequest.Builder(ctx)
            .data(item.poster)
            .size(iw, ih)
            .deviceImage(deviceClass, isMediatek)
            .build()
    }

    val titleFontSize = when (deviceClass) {
        DeviceClass.LOW -> 26.sp
        DeviceClass.MID -> 32.sp
        DeviceClass.HIGH -> 48.sp
    }
    val titleLetterSpacing = when (deviceClass) {
        DeviceClass.LOW -> 3.sp
        DeviceClass.MID -> 4.sp
        DeviceClass.HIGH -> 8.sp
    }
    val titleFontWeight = when (deviceClass) {
        DeviceClass.HIGH -> FontWeight.Black
        else -> FontWeight.Light
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Premium color-only backdrop: no stretched images
                val w = size.width
                val h = size.height

                if (deviceClass != DeviceClass.LOW) {
                    // Radial glow from center (accent color)
                    val glowAlpha = if (deviceClass == DeviceClass.HIGH) 0.4f else 0.25f
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = glowAlpha),
                                accentColor.copy(alpha = glowAlpha * 0.2f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.5f),
                            radius = w * 0.9f
                        )
                    )
                }

                // Focus glow border
                if (isFocused) {
                    val strokeWidth = 3.dp.toPx()
                    val cornerR = 12.dp.toPx()
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.8f),
                        topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f),
                        size = androidx.compose.ui.geometry.Size(w - strokeWidth, h - strokeWidth),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                    )
                }
            }
    ) {
        // Gradient overlay (base horizontal)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (deviceClass == DeviceClass.HIGH) 0.45f else 0.3f),
                            accentColor.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        endX = 1400f
                    )
                )
        )

        // Layer 3: Content
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141414))
            ) {
                AsyncImage(
                    model = posterRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = PlaceholderDark,
                    error = PlaceholderDark
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title.uppercase(),
                    color = OnSurface,
                    fontSize = titleFontSize,
                    fontWeight = titleFontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = titleLetterSpacing
                )

                // Meta badge row
                MetaBadge(item = item, deviceClass = deviceClass)

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetaBadge(
    item: Movie,
    deviceClass: DeviceClass
) {
    val showAnimation = deviceClass == DeviceClass.HIGH
    val metaItems = remember(item) {
        buildList {
            if (!item.rating.isNullOrEmpty()) add("IMDb ${item.rating}")
            item.year?.let { add(it.toString()) }
            if (!item.quality.isNullOrEmpty()) add(item.quality.uppercase())
            item.contentType?.let { add(it.uppercase()) }
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(if (showAnimation) 400 else 0))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(vertical = if (deviceClass == DeviceClass.HIGH) 16.dp else 10.dp)
        ) {
            if (!item.rating.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .background(Gold, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "\u2605 ${item.rating}",
                        color = Color.Black,
                        fontSize = if (deviceClass == DeviceClass.HIGH) 15.sp else 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            metaItems.drop(1).forEach { text ->
                Text(
                    text = text,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = if (deviceClass == DeviceClass.HIGH) 15.sp else 14.sp
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    brandColor: Color,
    deviceClass: DeviceClass,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val targetSizeDp = if (isSelected) 10.dp else 6.dp
            val targetAlpha = if (isSelected) 1f else 0.25f

            val animatedSize by animateFloatAsState(
                targetValue = targetSizeDp.value,
                animationSpec = tween(if (deviceClass == DeviceClass.HIGH) 400 else 200),
                label = "dotSize"
            )
            val animatedAlpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(if (deviceClass == DeviceClass.HIGH) 300 else 150),
                label = "dotAlpha"
            )

            Box(
                modifier = Modifier
                    .size(animatedSize.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) brandColor.copy(alpha = animatedAlpha)
                        else Color.White.copy(alpha = animatedAlpha)
                    )
            )
        }
    }
}
