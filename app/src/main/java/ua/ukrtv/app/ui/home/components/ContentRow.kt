package ua.ukrtv.app.ui.home.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
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
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalFormFactor
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.ui.theme.PhoneCardDefaults
import ua.ukrtv.app.ui.theme.PhoneGridDefaults
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.Shapes
import ua.ukrtv.app.util.DeviceClass

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ContentRow(
    title: String,
    items: List<Movie>,
    brandColor: Color,
    onItemClick: (Movie) -> Unit,
    onItemDismiss: ((Movie) -> Unit)? = null,
    onItemFocused: ((Movie) -> Unit)? = null,
    useWideCards: Boolean = false,
    useLargeCards: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val formFactor = LocalFormFactor.current
    when (formFactor) {
        FormFactor.TV -> TvContentRow(title, items, brandColor, onItemClick, onItemDismiss, onItemFocused, useWideCards, useLargeCards, trailingContent)
        FormFactor.PHONE, FormFactor.TABLET -> PhoneContentRow(title, items, brandColor, onItemClick, useWideCards, trailingContent)
    }
}

@Composable
private fun PhoneContentRow(
    title: String,
    items: List<Movie>,
    brandColor: Color,
    onItemClick: (Movie) -> Unit,
    useWideCards: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val titleColor = remember(brandColor) { brandColor.copy(alpha = 0.7f) }

    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        if (title.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = PhoneGridDefaults.horizontalPadding, bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(brandColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title.uppercase(),
                    color = titleColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = PhoneGridDefaults.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(PhoneGridDefaults.columnSpacing)
        ) {
            itemsIndexed(
                items = items,
                key = { _, it -> it.pageUrl },
                contentType = { _, _ -> if (useWideCards) "wide" else "movie" }
            ) { _, item ->
                val onClick = remember(item) { { onItemClick(item) } }
                if (useWideCards) {
                    ContinueWatchingCard(movie = item, onClick = onClick)
                } else {
                    MovieCard(movie = item, onClick = onClick)
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvContentRow(
    title: String,
    items: List<Movie>,
    brandColor: Color,
    onItemClick: (Movie) -> Unit,
    onItemDismiss: ((Movie) -> Unit)? = null,
    onItemFocused: ((Movie) -> Unit)? = null,
    useWideCards: Boolean = false,
    useLargeCards: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val titleColor = remember(brandColor) { brandColor.copy(alpha = 0.7f) }
    val deviceClass = LocalDeviceClass.current
    val isMediatek = LocalIsMediatek.current
    val cardScale = remember(deviceClass) {
        when (deviceClass) {
            DeviceClass.LOW -> 0.75f
            DeviceClass.MID -> 1.0f
            DeviceClass.HIGH -> 1.25f
        }
    }
    val rowT = remember { System.currentTimeMillis() }
    if (ua.ukrtv.app.BuildConfig.DEBUG) {
        LaunchedEffect(items) {
            ua.ukrtv.app.util.AppLogger.d("ContentRow", "'$title' rendered ${items.size} items at ${System.currentTimeMillis() - rowT}ms")
        }
    }

    val lazyListState = rememberLazyListState()
    val (rowFocus, firstItemFocus) = remember { FocusRequester.createRefs() }
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    val animateEntrance = deviceClass != DeviceClass.LOW && !isMediatek
    val staggerMs = when { animateEntrance && deviceClass == DeviceClass.HIGH -> 60; animateEntrance && deviceClass == DeviceClass.MID -> 30; else -> 0 }
    val animDuration = when { animateEntrance && deviceClass == DeviceClass.HIGH -> 300; animateEntrance && deviceClass == DeviceClass.MID -> 200; else -> 0 }
    val enterAnimated = animateEntrance
    val enterStartScale = if (animateEntrance && deviceClass == DeviceClass.HIGH) 0.92f else 1f
    val enterTranslateYDp = if (animateEntrance && deviceClass == DeviceClass.HIGH) 12f else 0f
    val scope = rememberCoroutineScope()

    // Re-focus row content when deviceClass changes to prevent focus escape to TopBar
    val initialDeviceClass = remember { deviceClass }
    LaunchedEffect(deviceClass) {
        if (deviceClass != initialDeviceClass && items.isNotEmpty()) {
            withFrameNanos { }
            firstItemFocus.requestFocus()
        }
    }

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        if (title.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = GridDefaults.horizontalPadding, bottom = 12.dp, top = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(brandColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title.uppercase(),
                    color = titleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rowFocus)
                .onFocusChanged { state ->
                    if (state.isFocused && items.isNotEmpty()) {
                        scope.launch {
                            withFrameNanos { }
                            firstItemFocus.requestFocus()
                        }
                    }
                },
            state = lazyListState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
            contentPadding = PaddingValues(horizontal = GridDefaults.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(GridDefaults.columnSpacing)
        ) {
            if (items.isEmpty()) {
                items(6, key = { "shimmer_$it" }) { shimmerIndex ->
                    val shimmerWidth = (if (useWideCards) CardDefaults.wideWidth else CardDefaults.compactWidth) * cardScale
                    val shimmerHeight = (if (useWideCards) CardDefaults.wideHeight else CardDefaults.compactHeight) * cardScale
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
                key = { _, it -> it.pageUrl },
                contentType = { _, _ -> if (useWideCards) "wide" else "movie" }
            ) { index, item ->
                val isFirst = index == 0
                val isLast = index == items.lastIndex && trailingContent == null
                val itemModifier = if (isFirst) Modifier.focusRequester(firstItemFocus) else Modifier

                val keyBlockMod = Modifier.onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        isFirst && event.key == Key.DirectionLeft -> true
                        isLast && event.key == Key.DirectionRight -> true
                        else -> false
                    }
                }

                val lastSoundTime = remember { mutableLongStateOf(0L) }
                val focusMod = itemModifier.then(keyBlockMod).onFocusChanged { state ->
                    if (state.isFocused) {
                        onItemFocused?.invoke(item)
                        val now = System.currentTimeMillis()
                        if (now - lastSoundTime.longValue > 80L) {
                            lastSoundTime.longValue = now
                            audioManager?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT)
                        }
                    }
                }

                // Entrance animation
                val enterAlpha = remember { Animatable(if (enterAnimated) 0f else 1f) }
                val enterScale = remember { Animatable(enterStartScale) }
                val density = LocalDensity.current
                val translateYPx = remember(enterTranslateYDp) { with(density) { enterTranslateYDp.dp.toPx() } }
                val enterTranslateY = remember { Animatable(translateYPx) }
                val animated = remember { mutableStateOf(!enterAnimated) }
                LaunchedEffect(animated.value) {
                    if (!animated.value) {
                        delay(index * staggerMs.toLong())
                        launch {
                            enterScale.animateTo(1f, tween(animDuration))
                        }
                        if (translateYPx > 0f) {
                            launch {
                                enterTranslateY.animateTo(0f, tween(animDuration))
                            }
                        }
                        enterAlpha.animateTo(1f, tween(animDuration))
                        animated.value = true
                    }
                }

                val entranceMod = focusMod
                    .alpha(enterAlpha.value)
                    .scale(enterScale.value)
                    .then(
                        if (translateYPx > 0f)
                            Modifier.graphicsLayer { translationY = enterTranslateY.value }
                        else Modifier
                    )

                val onClick = remember(item) { { onItemClick(item) } }
                val onDismiss = onItemDismiss?.let { remember(item) { { it(item) } } }

                if (useWideCards) {
                    ContinueWatchingCard(
                        movie = item,
                        brandColor = brandColor,
                        onClick = onClick,
                        onLongClick = onDismiss,
                        onDismiss = onDismiss,
                        modifier = entranceMod
                    )
                } else {
                    MovieCard(
                        movie = item,
                        brandColor = brandColor,
                        width = (if (useLargeCards) CardDefaults.posterWidth * 1.15f else CardDefaults.posterWidth) * cardScale,
                        height = (if (useLargeCards) CardDefaults.posterHeight * 1.15f else CardDefaults.posterHeight) * cardScale,
                        onClick = onClick,
                        onDismiss = onDismiss,
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
