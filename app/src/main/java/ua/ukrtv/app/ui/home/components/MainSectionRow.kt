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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
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

    // YouTV row fade: moving down swaps instantly, moving up fades the new row in.
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

    var hadFocus by remember { mutableStateOf(false) }
    var rowFocusedNow by remember { mutableStateOf(false) }

    // Exact YouTV row heights per card type (MainVerticalGrid.V1, tween 250ms).
    // Collapsed rows peek as a 52dp strip under the hero; they expand only on focus.
    val targetRowHeight = when {
        !rowFocusedNow -> YouTv.gridClosedH
        section.useLargeCards -> YouTv.collectionRowH
        else -> YouTv.videoRowH
    }
    val animatedRowHeight by animateDpAsState(
        targetValue = targetRowHeight,
        animationSpec = tween(250),
        label = "mainRowHeight"
    )

    val lazyListState = rememberLazyListState()
    val (rowFocusRef, firstItemFocus, trailingFocus, restoreFocus) = remember { FocusRequester.createRefs() }

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    fun keyOf(movie: Movie) = "${movie.pageUrl}_${movie.season ?: ""}_${movie.episode ?: ""}"
    val restoreKey = restoreMovie?.let { keyOf(it) }

    // After a section switch the previously focused item is gone — pull focus back into the new row.
    LaunchedEffect(section.id) {
        if (!hadFocus) return@LaunchedEffect
        var granted = false
        for (attempt in 0 until 4) {
            if (granted) break
            withFrameNanos { }
            granted = runCatching { firstItemFocus.requestFocus() }.getOrDefault(false)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Row header appears only while the row is expanded/focused (YouTV closed grids have none).
        val headerAlpha by animateFloatAsState(if (rowFocusedNow) 1f else 0f, tween(250), label = "headerAlpha")
        val headerHeight by animateDpAsState(if (rowFocusedNow) 26.dp else 0.dp, tween(250), label = "headerH")
        Box(
            modifier = Modifier
                .height(headerHeight)
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
                            onRowFocusChange(state.hasFocus)
                            hadFocus = state.hasFocus || hadFocus
                            rowFocusedNow = state.hasFocus
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionUp -> if (index > 0) {
                                    onSectionChange(index - 1); true
                                } else false
                                Key.DirectionDown -> if (index < sections.lastIndex) {
                                    onSectionChange(index + 1); true
                                } else false
                                else -> false
                            }
                        },
                    state = lazyListState,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
                    contentPadding = PaddingValues(horizontal = YouTv.startLine),
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
                        val isFirst = itemIndex == 0 && !section.isLoading
                        val isLast = itemIndex == section.items.lastIndex

                        val keyBlockMod = remember(isFirst, isLast) {
                            Modifier.onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when {
                                    isFirst && event.key == Key.DirectionLeft -> true
                                    isLast && event.key == Key.DirectionRight -> true
                                    else -> false
                                }
                            }
                        }

                        val isRestoreTarget = restoreKey != null && keyOf(item) == restoreKey
                        val focusModifier = remember(isFirst, isLast, isRestoreTarget, itemIndex) {
                            var mod: Modifier = keyBlockMod.focusProperties {
                                exit = { focusDirection ->
                                    if (focusDirection == FocusDirection.Right && itemIndex == section.items.lastIndex) {
                                        if (isLast && section.categoryKey != null) trailingFocus
                                        else FocusRequester.Cancel
                                    } else {
                                        FocusRequester.Default
                                    }
                                }
                            }
                            if (isFirst) mod = mod.then(Modifier.focusRequester(firstItemFocus))
                            if (isRestoreTarget) mod = mod.then(Modifier.focusRequester(restoreFocus))
                            mod
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
                        Box(
                            modifier = Modifier
                                .focusRequester(trailingFocus)
                                .focusTarget()
                        ) {
                            TrendsTrailingButton(
                                brandColor = brandColor,
                                onClick = { onSeeAllClick(section) },
                                useLargeCards = section.useLargeCards,
                                provider = section.items.firstOrNull()?.provider ?: providerHint
                            )
                        }
                    }
                }

                // Side chevrons while the row scrolls horizontally (YouTV arrow_left/arrow_rigth).
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

/**
 * Card matching YouTV card_video.xml: rounded poster with white stroke on focus,
 * optional seek line and title/genre lines BELOW the poster.
 */
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

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "youTvCardScale"
    )

    val cardW = if (large) YouTv.collectionCardW else YouTv.vodCardW
    val cardH = if (large) YouTv.collectionCardH else YouTv.vodCardH
    val shape = RoundedCornerShape(YouTv.cardRadius)

    val posterStyle = PosterStyle.forProvider(movie.provider)
    val (iw, ih) = when (posterStyle) {
        PosterStyle.WIDE -> when (deviceClass) { DeviceClass.LOW -> 266 to 148; DeviceClass.MID -> 532 to 296; else -> 798 to 444 }
        PosterStyle.VERTICAL -> when (deviceClass) { DeviceClass.LOW -> 120 to 164; DeviceClass.MID -> 240 to 328; else -> 360 to 492 }
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
            .graphicsLayer { scaleX = scale; scaleY = scale }
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
                        shadowElevation = 18.dp.toPx()
                        ambientShadowColor = Color.Black.copy(alpha = 0.6f)
                        spotShadowColor = accentColor.copy(alpha = 0.45f)
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
                        .padding(4.dp)
                        .background(pColor.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    androidx.tv.material3.Text(
                        text = p.uppercase(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Seek progress line under the poster (card_video.xml seekbar slot).
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

        // Title + genre lines below the poster.
        androidx.tv.material3.Text(
            text = movie.title,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = Montserrat,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (showProgressLine) 4.dp else 6.dp)
        )
        if (!large) {
            val genre = movie.genres.firstOrNull() ?: movie.quality?.uppercase() ?: ""
            if (genre.isNotBlank()) {
                androidx.tv.material3.Text(
                    text = genre,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = Montserrat,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
