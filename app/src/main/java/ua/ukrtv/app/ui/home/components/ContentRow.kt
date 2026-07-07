package ua.ukrtv.app.ui.home.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.home.ContinueWatchingCard
import ua.ukrtv.app.ui.home.MovieCard
import ua.ukrtv.app.ui.components.ShimmerBox
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.GridDefaults
import ua.ukrtv.app.ui.theme.Shapes

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
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val titleColor = remember(brandColor) { brandColor.copy(alpha = 0.7f) }
    val rowT = remember { System.currentTimeMillis() }
    val focusedItemKeyState = remember { mutableStateOf<String?>(null) }
    val focusedItemKey = focusedItemKeyState.value

    LaunchedEffect(items) {
        ua.ukrtv.app.util.AppLogger.d("ContentRow", "'$title' rendered ${items.size} items at ${System.currentTimeMillis() - rowT}ms")
    }

    val context = LocalContext.current
    LaunchedEffect(items.take(5)) {
        val loader = Coil.imageLoader(context)
        items.take(5).forEach { movie ->
            loader.enqueue(ImageRequest.Builder(context)
                .data(movie.poster)
                .size(180, 270)
                .allowRgb565(true)
                .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                .build())
        }
    }

    val lazyListState = rememberLazyListState()

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        var isVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            isVisible = true
        }

        if (title.isNotEmpty()) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(spring(dampingRatio = 0.6f, stiffness = 300f)),
            ) {
                Text(
                    text = title.uppercase(),
                    color = titleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = GridDefaults.horizontalPadding, bottom = 12.dp, top = 32.dp)
                )
            }
        }

        val (rowFocus, firstItemFocus) = remember { FocusRequester.createRefs() }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rowFocus)
                .focusProperties { onEnter = { firstItemFocus.requestFocus(); FocusRequester.Cancel } },
            state = lazyListState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
            contentPadding = PaddingValues(horizontal = GridDefaults.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(GridDefaults.columnSpacing)
        ) {
            if (items.isEmpty()) {
                items(6, key = { "shimmer_$it" }) { shimmerIndex ->
                    val shimmerWidth = if (useWideCards) CardDefaults.wideWidth else CardDefaults.compactWidth
                    val shimmerHeight = if (useWideCards) CardDefaults.wideHeight else CardDefaults.compactHeight
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
                val isExpanded = focusedItemKey == item.pageUrl
                val itemModifier = if (items.firstOrNull() == item) Modifier.focusRequester(firstItemFocus) else Modifier

                val audioManager = LocalContext.current.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val lastSoundTime = remember { mutableLongStateOf(0L) }
                val focusMod = itemModifier.onFocusChanged { state ->
                    focusedItemKeyState.value = if (state.isFocused) item.pageUrl else null
                    if (state.isFocused) {
                        onItemFocused?.invoke(item)
                        val now = System.currentTimeMillis()
                        if (now - lastSoundTime.longValue > 80L) {
                            lastSoundTime.longValue = now
                            audioManager?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT)
                        }
                    }
                }

                if (useWideCards) {
                    ContinueWatchingCard(
                        movie = item,
                        brandColor = brandColor,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onClick = { onItemClick(item) },
                        onDismiss = onItemDismiss?.let { { it(item) } },
                        modifier = focusMod
                    )
                } else {
                    MovieCard(
                        movie = item,
                        brandColor = brandColor,
                        width = if (useLargeCards) CardDefaults.posterWidth * 1.15f else CardDefaults.posterWidth,
                        height = if (useLargeCards) CardDefaults.posterHeight * 1.15f else CardDefaults.posterHeight,
                        isExpanded = isExpanded,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onClick = { onItemClick(item) },
                        onDismiss = onItemDismiss?.let { { it(item) } },
                        modifier = focusMod
                    )
                }
            }
        }
    }
}
