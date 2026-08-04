package ua.ukrtv.app.ui.home.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import ua.ukrtv.app.ui.theme.PhoneCardDefaults
import ua.ukrtv.app.ui.theme.PhoneGridDefaults
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.Shapes
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.util.PosterColorCache

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
    restoreMovie: Movie? = null
) {
    val formFactor = LocalFormFactor.current
    when (formFactor) {
        FormFactor.TV -> TvContentRow(title, items, brandColor, onItemClick, onItemDismiss, onItemFocused, useLargeCards, trailingContent, isLoading, sharedTransitionScope, animatedContentScope, providerHint, restoreMovie)
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
    val rowEntrance = remember { Animatable(0f) }
    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            rowEntrance.animateTo(1f, tween(250))
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
                    val start = index * 0.04f
                    alpha = ((rowEntrance.value - start) * 5f).coerceIn(0f, 1f)
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
    restoreMovie: Movie? = null
) {
    val deviceClass = LocalDeviceClass.current
    val isMediatek = LocalIsMediatek.current
    val cardScale = remember(deviceClass) {
        when (deviceClass) {
            DeviceClass.LOW -> 0.75f
            DeviceClass.MID -> 1.0f
            DeviceClass.HIGH -> 1.25f
        }
    }
    val lazyListState = rememberLazyListState()
    val (rowFocus, firstItemFocus, trailingFocus) = remember { FocusRequester.createRefs() }
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    val restoreKey = restoreMovie?.let { "${it.pageUrl}_${it.season ?: ""}_${it.episode ?: ""}" }
    val targetIndex = if (restoreKey != null) items.indexOfFirst { "${it.pageUrl}_${it.season ?: ""}_${it.episode ?: ""}" == restoreKey } else -1
    val targetFocus = remember { FocusRequester() }
    var didRestore by remember { mutableStateOf(false) }
    LaunchedEffect(items, restoreMovie) {
        if (didRestore || targetIndex < 0) return@LaunchedEffect
        lazyListState.animateScrollToItem(targetIndex)
        withFrameNanos { }
        if (runCatching { targetFocus.requestFocus() }.getOrDefault(false)) {
            didRestore = true
        }
    }

    val animateEntrance = deviceClass == DeviceClass.HIGH && !isMediatek
    val rowEntrance = remember { Animatable(0f) }
    LaunchedEffect(items) {
        if (animateEntrance && items.isNotEmpty()) {
            rowEntrance.animateTo(1f, tween(300))
        } else {
            rowEntrance.snapTo(1f)
        }
    }

    val scope = rememberCoroutineScope()

    val posterStyle = remember(items, providerHint) {
        val provider = items.firstOrNull()?.provider ?: providerHint
        PosterStyle.forProvider(provider)
    }
    val tvDims = ProviderSizes.card(posterStyle)

    val rowHeight = remember(useLargeCards, cardScale, posterStyle) {
        val baseHeight = if (useLargeCards) tvDims.height * 1.15f
        else tvDims.height
        (baseHeight * cardScale) + 32.dp
    }

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        SectionHeader(
            title = title,
            brandColor = brandColor,
            modifier = Modifier.padding(start = GridDefaults.horizontalPadding, bottom = 12.dp, top = 32.dp),
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
                        if (state.isFocused) {
                            scope.launch {
                                withFrameNanos { }
                                if (items.isNotEmpty()) firstItemFocus.requestFocus()
                                else if (trailingContent != null) trailingFocus.requestFocus()
                            }
                        }
                    },
                state = lazyListState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
                contentPadding = PaddingValues(horizontal = GridDefaults.horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(GridDefaults.columnSpacing)
            ) {
                if (items.isEmpty() && isLoading) {
                    items(6, key = { "shimmer_$it" }) { shimmerIndex ->
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
                    val isLast = index == items.lastIndex && trailingContent == null
                    val itemModifier = remember(isFirst) { if (isFirst) Modifier.focusRequester(firstItemFocus) else Modifier }

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

                    val lastSoundTime = remember { mutableLongStateOf(0L) }
                    val isRestoreTarget = restoreKey != null && "${item.pageUrl}_${item.season ?: ""}_${item.episode ?: ""}" == restoreKey
                    val restoreMod = if (isRestoreTarget) Modifier.focusRequester(targetFocus) else Modifier
                    val focusMod = remember(item, onItemFocused, audioManager, keyBlockMod, itemModifier, restoreMod) {
                        itemModifier
                            .focusProperties {
                                exit = { focusDirection ->
                                    if (focusDirection == androidx.compose.ui.focus.FocusDirection.Right) {
                                        if (isLast) androidx.compose.ui.focus.FocusRequester.Cancel
                                        else if (index == items.lastIndex && trailingContent != null) trailingFocus
                                        else androidx.compose.ui.focus.FocusRequester.Default
                                    } else {
                                        androidx.compose.ui.focus.FocusRequester.Default
                                    }
                                }
                            }
                            .then(keyBlockMod)
                            .then(restoreMod)
                            .onFocusChanged { state ->
                            if (state.isFocused) {
                                onItemFocused?.invoke(item)
                                val now = System.currentTimeMillis()
                                if (now - lastSoundTime.longValue > 150L) {
                                    lastSoundTime.longValue = now
                                    audioManager?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT)
                                }
                            }
                        }
                    }

                    val entranceMod = focusMod
                        .graphicsLayer {
                            if (animateEntrance) {
                                val start = index * 0.05f
                                alpha = ((rowEntrance.value - start) * 4f).coerceIn(0f, 1f)
                                val s = 0.95f + (alpha * 0.05f)
                                scaleX = s
                                scaleY = s
                                translationY = (1f - alpha) * 20.dp.toPx()
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
                        val cwWidth = (if (useLargeCards) tvDims.width * 1.15f else tvDims.width) * cardScale
                        val cwHeight = (if (useLargeCards) tvDims.height * 1.15f else tvDims.height) * cardScale
                        ContinueWatchingCard(
                            movie = item,
                            brandColor = brandColor,
                            accentColor = accentColor,
                            width = cwWidth,
                            height = cwHeight,
                            onClick = onClick,
                            onLongClick = onDismiss,
                            onDismiss = onDismiss,
                            modifier = entranceMod
                        )
                    } else {
                        MovieCard(
                            movie = item,
                            brandColor = brandColor,
                            accentColor = accentColor,
                            width = (if (useLargeCards) tvDims.width * 1.15f else tvDims.width) * cardScale,
                            height = (if (useLargeCards) tvDims.height * 1.15f else tvDims.height) * cardScale,
                            onClick = onClick,
                            onDismiss = onDismiss,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            modifier = entranceMod
                        )
                    }
                }

                if (trailingContent != null) {
                    item(key = "__trailing", contentType = "trailing") {
                        val trailingInteractionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .focusRequester(trailingFocus)
                                .focusable(interactionSource = trailingInteractionSource)
                        ) {
                            trailingContent()
                        }
                    }
                }
            }
        }
    }
}
