package ua.ukrtv.app.ui.home.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.home.ContinueWatchingCard
import ua.ukrtv.app.ui.home.MovieCard
import ua.ukrtv.app.ui.components.ShimmerBox
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.GridDefaults
import ua.ukrtv.app.ui.theme.PosterStyle
import ua.ukrtv.app.ui.theme.ProviderSizes
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalFormFactor
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.ui.theme.LocalPerformanceRevision
import ua.ukrtv.app.ui.theme.PhoneCardDefaults
import ua.ukrtv.app.ui.theme.PhoneGridDefaults
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.Shapes
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.util.PosterColorCache
import ua.ukrtv.app.util.maxShimmerItems

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ContentRow(
    title: String,
    items: List<Movie>,
    brandColor: Color,
    onItemClick: (Movie) -> Unit,
    onItemDismiss: ((Movie) -> Unit)? = null,
    onItemFocused: ((Movie) -> Unit)? = null,
    useLargeCards: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null,
    isLoading: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    providerHint: String? = null,
    restoreMovie: Movie? = null,
    restoreWindowOpen: () -> Boolean = { false },
    onRestoreHandled: () -> Unit = {},
    rowId: String? = null,
    focusedRowId: String? = null,
    onRowFocused: (() -> Unit)? = null
) {
    val formFactor = LocalFormFactor.current
    when (formFactor) {
        FormFactor.TV -> TvContentRow(title, items, brandColor, onItemClick, onItemDismiss, onItemFocused, useLargeCards, trailingContent, isLoading, sharedTransitionScope, animatedContentScope, providerHint, restoreMovie, restoreWindowOpen, onRestoreHandled, rowId, focusedRowId, onRowFocused)
        FormFactor.PHONE, FormFactor.TABLET -> PhoneContentRow(title, items, brandColor, onItemClick, trailingContent, isLoading, sharedTransitionScope, animatedContentScope, providerHint)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PhoneContentRow(
    title: String,
    items: List<Movie>,
    brandColor: Color,
    onItemClick: (Movie) -> Unit,
    trailingContent: @Composable (() -> Unit)? = null,
    isLoading: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    providerHint: String? = null
) {
    val entranceEpoch = LocalPerformanceRevision.current
    var entranceDone by rememberSaveable(entranceEpoch) { mutableStateOf(false) }
    val rowEntrance = remember { Animatable(0f) }
    LaunchedEffect(items) {
        if (!entranceDone && items.isNotEmpty()) {
            entranceDone = true
            rowEntrance.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            rowEntrance.snapTo(1f)
        }
    }

    val posterStyle = remember(items, providerHint) {
        val provider = items.firstOrNull()?.provider ?: providerHint
        PosterStyle.forProvider(provider)
    }
    val phoneDims = ProviderSizes.phoneCard(posterStyle)

    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        SectionHeader(
            title = title,
            brandColor = brandColor,
            modifier = Modifier.padding(start = PhoneGridDefaults.horizontalPadding, bottom = 8.dp),
            isPhone = true
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = PhoneGridDefaults.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(PhoneGridDefaults.columnSpacing)
        ) {
            if (isLoading && items.isEmpty()) {
                items(5, key = { "phone_shimmer_$it" }) {
                    ShimmerBox(
                        modifier = Modifier.width(phoneDims.width).height(phoneDims.height),
                        shape = Shapes.card
                    )
                }
            }

            itemsIndexed(
                items = items,
                key = { _, it -> "${it.pageUrl}_${it.season ?: ""}_${it.episode ?: ""}" },
                contentType = { _, it -> if (it.watchProgress != null) "wide" else "movie" }
            ) { index, item ->
                val onClick = remember(item) { { onItemClick(item) } }
                val accentColor = remember(item.brandColor) {
                    item.brandColor?.let { try { Color(android.graphics.Color.parseColor(it)) } catch(_: Exception) { null } }
                        ?: PosterColorCache.getCached(item.poster)
                        ?: brandColor
                }
                val entranceMod = Modifier.graphicsLayer {
                    val start = index * 0.035f
                    val progress = ((rowEntrance.value - start) * 5f).coerceIn(0f, 1f)
                    alpha = progress
                    val s = 0.93f + (progress * 0.07f)
                    scaleX = s
                    scaleY = s
                    translationY = (1f - progress) * 24.dp.toPx()
                }

                if (item.watchProgress != null) {
                    ContinueWatchingCard(
                        movie = item,
                        brandColor = brandColor,
                        accentColor = accentColor,
                        width = phoneDims.width,
                        height = phoneDims.height,
                        onClick = onClick,
                        modifier = entranceMod
                    )
                } else {
                    MovieCard(
                        movie = item,
                        brandColor = brandColor,
                        accentColor = accentColor,
                        width = phoneDims.width,
                        height = phoneDims.height,
                        onClick = onClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        modifier = entranceMod
                    )
                }
            }

            if (trailingContent != null) {
                item(key = "__trailing") {
                    trailingContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun TvContentRow(
    title: String,
    items: List<Movie>,
    brandColor: Color,
    onItemClick: (Movie) -> Unit,
    onItemDismiss: ((Movie) -> Unit)? = null,
    onItemFocused: ((Movie) -> Unit)? = null,
    useLargeCards: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null,
    isLoading: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    providerHint: String? = null,
    restoreMovie: Movie? = null,
    restoreWindowOpen: () -> Boolean = { false },
    onRestoreHandled: () -> Unit = {},
    rowId: String? = null,
    focusedRowId: String? = null,
    onRowFocused: (() -> Unit)? = null
) {
    val deviceClass = LocalDeviceClass.current
    val isMediatek = LocalIsMediatek.current
    val cardScale = remember(deviceClass) {
        if (deviceClass == DeviceClass.HIGH) 1.15f else 0.75f
    }
    // "Use large cards" is used for trending rows; keep it subtle on the premium preset
    val largeCardScale = remember(deviceClass) {
        if (deviceClass == DeviceClass.HIGH) 1.25f else 1.15f
    }
    val showFocusPanel = deviceClass == DeviceClass.HIGH
    // Rail Fade uses plain alpha compositing — cheap even on Mediatek GPUs.
    val rowFadeEnabled = deviceClass == DeviceClass.HIGH
    val isRowFocused = rowId == null || focusedRowId == rowId
    val lazyListState = rememberLazyListState()
    val (rowFocus, firstItemFocus, trailingFocus) = remember { FocusRequester.createRefs() }
    var hadFocus by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    fun keyOf(movie: Movie) = "${movie.pageUrl}_${movie.season ?: ""}_${movie.episode ?: ""}"

    val restoreKey = restoreMovie?.let { keyOf(it) }
    val targetFocus = remember { FocusRequester() }
    LaunchedEffect(items) {
        if (restoreKey == null || !restoreWindowOpen()) return@LaunchedEffect
        val targetIndex = items.indexOfFirst { keyOf(it) == restoreKey }
        if (targetIndex < 0) return@LaunchedEffect
        onRestoreHandled()
        lazyListState.scrollToItem(targetIndex)
        var granted = false
        for (attempt in 0 until 3) {
            if (granted) break
            withFrameNanos { }
            granted = runCatching { targetFocus.requestFocus() }.getOrDefault(false)
        }
    }

    val animateEntrance = deviceClass == DeviceClass.HIGH && !isMediatek
    val entranceEpoch = LocalPerformanceRevision.current
    var entranceDone by rememberSaveable(entranceEpoch) { mutableStateOf(false) }
    val rowEntrance = remember { Animatable(0f) }
    LaunchedEffect(items) {
        if (animateEntrance && !entranceDone && items.isNotEmpty()) {
            entranceDone = true
            // Netflix-style: spring entrance with overshoot for bouncy feel
            rowEntrance.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            rowEntrance.snapTo(1f)
        }
    }

    val posterStyle = remember(items, providerHint) {
        val provider = items.firstOrNull()?.provider ?: providerHint
        PosterStyle.forProvider(provider)
    }
    val tvDims = ProviderSizes.card(posterStyle)

    val cardWidth = (if (useLargeCards) tvDims.width * largeCardScale else tvDims.width) * cardScale
    val cardHeight = (if (useLargeCards) tvDims.height * largeCardScale else tvDims.height) * cardScale

    // Rail Fade: non-focused rows dim dramatically so active shelf pops (Netflix pattern).
    val rowAlpha by animateFloatAsState(
        targetValue = if (!rowFadeEnabled || isRowFocused) 1f else 0.35f,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = Spring.StiffnessLow
        ),
        label = "rowFade"
    )

    val rowHeight = remember(useLargeCards, cardScale, largeCardScale, posterStyle, showFocusPanel) {
        val baseHeight = if (useLargeCards) tvDims.height * largeCardScale
        else tvDims.height
        (baseHeight * cardScale) + if (showFocusPanel) CardDefaults.focusPanelHeight + 8.dp else 32.dp
    }

    Column(
        modifier = Modifier
            .padding(bottom = if (showFocusPanel) 12.dp else 24.dp)
            .graphicsLayer { alpha = rowAlpha }
    ) {
        SectionHeader(
            title = title,
            brandColor = brandColor,
            modifier = Modifier.padding(
                start = GridDefaults.horizontalPadding,
                bottom = 12.dp,
                top = if (showFocusPanel) 16.dp else 32.dp
            ),
            isPhone = false
        )

        CompositionLocalProvider(
            LocalBringIntoViewSpec provides object : BringIntoViewSpec {
                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                    return offset + size * 0.5f - containerSize * 0.25f
                }
            }
        ) {
            LazyRow(
                modifier = Modifier
                    .height(rowHeight)
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRequester(rowFocus)
                    .onFocusChanged { state ->
                        // The row is a focus group (Focusability.Never), so `state.isFocused`
                        // is never true — entering the row surfaces as a hasFocus transition.
                        // We only use it to signal the rail fade, never to move focus.
                        val hasFocus = state.hasFocus
                        if (hasFocus && !hadFocus) {
                            onRowFocused?.invoke()
                        }
                        hadFocus = hasFocus
                    },
                state = lazyListState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
                contentPadding = PaddingValues(horizontal = GridDefaults.horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(GridDefaults.columnSpacing)
            ) {
                if (items.isEmpty() && isLoading) {
                    items(deviceClass.maxShimmerItems(), key = { "shimmer_$it" }) { shimmerIndex ->
                        val shimmerWidth = tvDims.width * cardScale
                        val shimmerHeight = tvDims.height * cardScale
                        ShimmerBox(
                            modifier = Modifier
                                .width(shimmerWidth)
                                .height(shimmerHeight),
                            shape = Shapes.card
                        )
                    }
                }

                itemsIndexed(
                    items = items,
                    key = { _, it -> "${it.pageUrl}_${it.season ?: ""}_${it.episode ?: ""}" },
                    contentType = { _, it -> if (it.watchProgress != null) "wide" else "movie" }
                ) { index, item ->
                    val isFirst = index == 0
                    val isLastItem = index == items.lastIndex

                    val keyBlockMod = remember(isFirst, isLastItem) {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                isFirst && event.key == Key.DirectionLeft -> true
                                else -> false
                            }
                        }
                    }

                    val itemKey = keyOf(item)
                    val lastSoundTime = remember { mutableLongStateOf(0L) }
                    val isRestoreTarget = restoreKey != null && keyOf(item) == restoreKey

                    val focusModifier = remember(item, keyBlockMod, isFirst, isRestoreTarget) {
                        var mod: Modifier = keyBlockMod
                            .focusProperties {
                                exit = { focusDirection ->
                                    if (focusDirection == FocusDirection.Right && isLastItem) {
                                        trailingFocus
                                    } else {
                                        androidx.compose.ui.focus.FocusRequester.Default
                                    }
                                }
                            }
                        if (isFirst) mod = mod.then(Modifier.focusRequester(firstItemFocus))
                        if (isRestoreTarget) mod = mod.then(Modifier.focusRequester(targetFocus))
                        mod
                    }

                    // onFocusChanged above a clickable/combinedClickable never fires (the clickable's
                    // FocusableNode blocks the focus-event walk), so focus side effects are driven by
                    // the card's interactionSource instead.
                    val onFocused = {
                        onItemFocused?.invoke(item)
                        val now = System.currentTimeMillis()
                        if (now - lastSoundTime.longValue > 150L) {
                            lastSoundTime.longValue = now
                            audioManager?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT)
                        }
                    }

                    val entranceMod = Modifier
                        .graphicsLayer {
                            if (animateEntrance) {
                                // Netflix-style stagger: each card enters with delay
                                val start = index * 0.04f
                                val progress = ((rowEntrance.value - start) * 4.5f).coerceIn(0f, 1f)
                                alpha = progress
                                // Scale from 0.92 → 1.0 (subtle zoom-in)
                                val s = 0.92f + (progress * 0.08f)
                                scaleX = s
                                scaleY = s
                                // Slide up 30dp → 0
                                translationY = (1f - progress) * 30.dp.toPx()
                            }
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }

                    val onClick = remember(item) { { onItemClick(item) } }
                    val onDismiss = onItemDismiss?.let { remember(item) { { it(item) } } }
                    val accentColor = remember(item.brandColor) {
                        item.brandColor?.let { try { Color(android.graphics.Color.parseColor(it)) } catch(_: Exception) { null } }
                            ?: PosterColorCache.getCached(item.poster)
                            ?: brandColor
                    }

                    if (item.watchProgress != null) {
                        ContinueWatchingCard(
                            movie = item,
                            brandColor = brandColor,
                            accentColor = accentColor,
                            width = cardWidth,
                            height = cardHeight,
                            showFocusPanel = showFocusPanel,
                            onClick = onClick,
                            onLongClick = onDismiss,
                            onDismiss = onDismiss,
                            modifier = entranceMod,
                            focusModifier = focusModifier,
                            onFocused = onFocused
                        )
                    } else {
                        MovieCard(
                            movie = item,
                            brandColor = brandColor,
                            accentColor = accentColor,
                            width = cardWidth,
                            height = cardHeight,
                            showFocusPanel = showFocusPanel,
                            onClick = onClick,
                            onDismiss = onDismiss,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            modifier = entranceMod,
                            focusModifier = focusModifier,
                            onFocused = onFocused
                        )
                    }
                }

                if (trailingContent != null) {
                    item(key = "__trailing", contentType = "trailing") {
                        Box(
                            modifier = Modifier
                                .focusRequester(trailingFocus)
                                .focusTarget()
                        ) {
                            trailingContent()
                        }
                    }
                }
            }
        }
    }
}
