package ua.ukrtv.app.ui.home.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.components.ShimmerBox
import ua.ukrtv.app.ui.theme.Montserrat
import ua.ukrtv.app.ui.theme.PlaceholderDark
import ua.ukrtv.app.ui.theme.PosterStyle
import ua.ukrtv.app.ui.theme.YouTv
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.util.PosterColorCache
import ua.ukrtv.app.util.maxShimmerItems

data class HomeSectionUi(
    val id: String,
    val title: String,
    val items: List<Movie>,
    val isLoading: Boolean,
    val useLargeCards: Boolean = false,
    val dismissable: Boolean = false,
    val categoryKey: String? = null
)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun MainSectionRow(
    sections: List<HomeSectionUi>,
    activeIndex: Int,
    onSectionChange: (Int) -> Unit,
    brandColor: Color,
    providerHint: String?,
    onMovieClick: (Movie) -> Unit,
    onItemDismiss: ((Movie) -> Unit)?,
    onItemFocused: ((Movie) -> Unit)?,
    onSeeAllClick: (HomeSectionUi) -> Unit,
    restoreMovie: Movie? = null,
    restoreWindowOpen: () -> Boolean = { false },
    onRestoreHandled: () -> Unit = {},
    onRowFocusChange: (Boolean) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    modifier: Modifier = Modifier
) {
    if (sections.isEmpty()) return
    val index = activeIndex.coerceIn(0, sections.lastIndex)
    val section = sections[index]
    val deviceClass = LocalDeviceClass.current
    val isMediatek = LocalIsMediatek.current

    val sectionAlpha = remember { Animatable(1f) }
    var lastIndex by remember { mutableStateOf(index) }
    LaunchedEffect(activeIndex, sections.size) {
        val safeIndex = activeIndex.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
        if (safeIndex == lastIndex) return@LaunchedEffect
        if (deviceClass == DeviceClass.LOW || isMediatek || safeIndex > lastIndex) {
            sectionAlpha.snapTo(1f)
        } else {
            sectionAlpha.snapTo(0f)
            sectionAlpha.animateTo(1f, tween(durationMillis = 100, delayMillis = 50))
        }
        lastIndex = safeIndex
    }

    var rowFocusedNow by remember { mutableStateOf(false) }

    // Reserve headroom above the posters equal to the top half of the focus-scale
    // overshoot, so a focused poster is never clipped by the row's clipToBounds().
    val youTvScale = if (deviceClass == DeviceClass.HIGH) 1.15f else 1.08f
    val cardH = if (section.useLargeCards) YouTv.collectionCardH else YouTv.vodCardH
    val focusHeadroom = cardH * (youTvScale - 1f) / 2f

    val targetRowHeight = when {
        !rowFocusedNow -> YouTv.gridClosedH + focusHeadroom
        section.useLargeCards -> YouTv.collectionRowH + focusHeadroom
        else -> YouTv.videoRowH + focusHeadroom
    }
    val animatedRowHeight by animateDpAsState(
        targetValue = targetRowHeight,
        animationSpec = tween(250),
        label = "mainRowHeight"
    )

    val lazyListState = rememberLazyListState()
    val (rowFocusRef, trailingFocus) = remember { FocusRequester.createRefs() }

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    // Set right before onSectionChange so the freshly-composed section knows the switch was
    // user-initiated and must hand focus to the (scrolled-to) first poster.
    var quickNav by remember { mutableStateOf(false) }

    // On any section change, reset scroll to the left edge. When the user initiated the switch
    // (quickNav), hand focus to the NEW row AFTER it has composed: rowFocusRef outlives every
    // card, so there is no race with an item being disposed mid-swap. Retried across frames —
    // the same proven pattern ContentRow uses for focus restore.
    LaunchedEffect(section.id) {
        runCatching { lazyListState.scrollToItem(0) }
        if (quickNav) {
            quickNav = false
            for (attempt in 0 until 5) {
                withFrameNanos { }
                if (runCatching { rowFocusRef.requestFocus() }.getOrDefault(false)) break
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        val headerAlpha by animateFloatAsState(if (rowFocusedNow) 1f else 0f, tween(250), label = "headerAlpha")
        val headerHeight by animateDpAsState(if (rowFocusedNow) 26.dp else 0.dp, tween(250), label = "headerH")
        Box(
            modifier = Modifier
                .height(headerHeight)
                .fillMaxWidth()
                .clipToBounds()
                .graphicsLayer { alpha = headerAlpha }
        ) {
            androidx.tv.material3.Text(
                text = section.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Montserrat,
                modifier = Modifier.padding(start = YouTv.startLine)
            )
        }

        CompositionLocalProvider(
            LocalBringIntoViewSpec provides object : BringIntoViewSpec {
                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                    return offset + size * 0.5f - containerSize * 0.25f
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = sectionAlpha.value }) {
                LazyRow(
                    modifier = Modifier
                        .height(animatedRowHeight)
                        .fillMaxWidth()
                        .clipToBounds()
                        .focusGroup()
                        .focusRequester(rowFocusRef)
                        .onFocusChanged { state ->
                            // While a quick switch is in flight (quickNav set), keep the row
                            // expanded even if hasFocus briefly drops due to item disposal.
                            onRowFocusChange(state.hasFocus || quickNav)
                            rowFocusedNow = state.hasFocus || quickNav
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionUp -> if (index > 0) {
                                    quickNav = true; onSectionChange(index - 1); true
                                } else false
                                Key.DirectionDown -> if (index < sections.lastIndex) {
                                    quickNav = true; onSectionChange(index + 1); true
                                } else false
                                else -> false
                            }
                        },
                    state = lazyListState,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
                    contentPadding = PaddingValues(start = YouTv.startLine, top = focusHeadroom, end = YouTv.startLine),
                    horizontalArrangement = Arrangement.spacedBy(YouTv.rowSpacing)
                ) {
                    if (section.items.isEmpty() && section.isLoading) {
                        items(deviceClass.maxShimmerItems(), key = { "shimmer_$it" }) {
                            ShimmerBox(
                                modifier = Modifier
                                    .width(if (section.useLargeCards) YouTv.collectionCardW else YouTv.vodCardW)
                                    .height(if (section.useLargeCards) YouTv.collectionCardH else YouTv.vodCardH),
                                shape = RoundedCornerShape(YouTv.cardRadius)
                            )
                        }
                    }

                    itemsIndexed(
                        items = section.items,
                        key = { _, it -> "${it.pageUrl}_${it.season ?: ""}_${it.episode ?: ""}" },
                        contentType = { _, it -> if (it.watchProgress != null) "wide" else "movie" }
                    ) { itemIndex, item ->
                        val isLast = itemIndex == section.items.lastIndex

                        val focusModifier = Modifier
                            .focusProperties {
                                exit = { focusDirection ->
                                    if (focusDirection == FocusDirection.Right && isLast) {
                                        trailingFocus
                                    } else {
                                        FocusRequester.Default
                                    }
                                }
                            }

                        val lastSoundTime = remember { mutableLongStateOf(0L) }
                        val onFocused = {
                            onItemFocused?.invoke(item)
                            val now = System.currentTimeMillis()
                            if (now - lastSoundTime.longValue > 150L) {
                                lastSoundTime.longValue = now
                                audioManager?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT)
                            }
                        }

                        val onClick = remember(item) { { onMovieClick(item) } }
                        val onDismiss = if (section.dismissable) onItemDismiss?.let { dismiss ->
                            remember(item) { { dismiss(item) } }
                        } else null
                        val accentColor = remember(item.brandColor) {
                            item.brandColor?.let {
                                try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
                            } ?: PosterColorCache.getCached(item.poster) ?: brandColor
                        }

                        YouTvCard(
                            movie = item,
                            large = section.useLargeCards,
                            showProgressLine = section.id == "continue_watching",
                            accentColor = accentColor,
                            onClick = onClick,
                            onDismiss = onDismiss,
                            focusModifier = focusModifier,
                            onFocused = onFocused
                        )
                    }

                    item(key = "__trailing", contentType = "trailing") {
                        TrendsTrailingButton(
                            brandColor = brandColor,
                            onClick = { onSeeAllClick(section) },
                            modifier = Modifier.focusRequester(trailingFocus),
                            useLargeCards = section.useLargeCards,
                            provider = section.items.firstOrNull()?.provider ?: providerHint
                        )
                    }
                }

                val canScrollBack by remember { derivedStateOf { lazyListState.canScrollBackward } }
                val canScrollFwd by remember { derivedStateOf { lazyListState.canScrollForward } }
                if (rowFocusedNow) {
                    if (canScrollBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 2.dp)
                                .size(40.dp)
                        )
                    }
                    if (canScrollFwd) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 2.dp)
                                .size(40.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTvCard(
    movie: Movie,
    large: Boolean,
    showProgressLine: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onDismiss: (() -> Unit)?,
    focusModifier: Modifier,
    onFocused: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val deviceClass = LocalDeviceClass.current
    val context = LocalContext.current

    LaunchedEffect(isFocused) { if (isFocused) onFocused() }

    val targetScale = if (deviceClass == DeviceClass.HIGH) 1.15f else 1.08f
    val scale by animateFloatAsState(
        targetValue = if (isFocused) targetScale else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "youTvCardScale"
    )

    val glowIntensity by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(300),
        label = "glowIntensity"
    )

    val cardW = if (large) YouTv.collectionCardW else YouTv.vodCardW
    val cardH = if (large) YouTv.collectionCardH else YouTv.vodCardH
    val shape = RoundedCornerShape(YouTv.cardRadius)

    val posterStyle = PosterStyle.forProvider(movie.provider)
    val (iw, ih) = when (posterStyle) {
        PosterStyle.WIDE -> if (deviceClass == DeviceClass.HIGH) 960 to 540 else 480 to 270
        PosterStyle.VERTICAL -> if (deviceClass == DeviceClass.HIGH) 480 to 720 else 240 to 360
    }

    val imageRequest = remember(movie.poster, deviceClass, posterStyle) {
        ImageRequest.Builder(context)
            .data(movie.poster)
            .size(iw, ih)
            .build()
    }

    Column(
        modifier = Modifier
            .width(cardW)
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            modifier = Modifier
                .width(cardW)
                .height(cardH)
                .then(focusModifier)
                .graphicsLayer {
                    clip = true
                    this.shape = shape
                    if (isFocused) {
                        shadowElevation = 30.dp.toPx() * glowIntensity
                        ambientShadowColor = Color.Black.copy(alpha = 0.5f)
                        spotShadowColor = accentColor.copy(alpha = 0.4f)
                    } else {
                        shadowElevation = 0f
                    }
                }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val isMenu = event.key == Key.Menu || event.key == Key.Settings
                    if (isMenu && onDismiss != null) { onDismiss(); true } else false
                }
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = PlaceholderDark,
                error = PlaceholderDark
            )

            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color.White, shape)
                )
            }

            movie.provider?.let { p ->
                val pColor = if (p == "Uakino") Color(0xFFFF6B35) else Color(0xFF4ECDC4)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .background(pColor.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    androidx.tv.material3.Text(
                        text = p.uppercase(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        if (showProgressLine && (movie.watchProgress ?: 0) > 0) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(((movie.watchProgress ?: 0) / 100f).coerceIn(0.02f, 1f))
                        .height(4.dp)
                        .background(accentColor, RoundedCornerShape(2.dp))
                )
            }
        }

        if (!large || isFocused) {
            androidx.tv.material3.Text(
                text = movie.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = Montserrat,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (showProgressLine) 4.dp else 6.dp, start = 2.dp)
            )
        }
        if (!large) {
            val genre = movie.genres.firstOrNull() ?: movie.quality?.uppercase() ?: ""
            if (genre.isNotBlank()) {
                androidx.tv.material3.Text(
                    text = genre,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}
