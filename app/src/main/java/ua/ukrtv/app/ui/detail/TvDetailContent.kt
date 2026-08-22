package ua.ukrtv.app.ui.detail

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text as TvText
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.home.MovieCard
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.ui.theme.Montserrat
import ua.ukrtv.app.ui.theme.PosterStyle
import ua.ukrtv.app.ui.theme.ProviderSizes
import ua.ukrtv.app.ui.theme.YouTv

/**
 * YouTV-style detail page (activity_video_detail.xml): fullscreen backdrop with a
 * bottom-heavy scrim, everything anchored to the bottom edge.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDetailContent(
    uiState: DetailUiState,
    onMovieClick: (Movie) -> Unit,
    onWatchClick: () -> Unit,
    onEpisodeClick: (Int, Episode, String?) -> Unit,
    onBackClick: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val state = uiState.detailState as? DetailState.Success ?: return
    val detail = state.detail
    val launchState = uiState.launchState
    val isInWatchlist = uiState.isInWatchlist
    val related = uiState.related
    val watchPercent = state.watchPercent
    val context = LocalContext.current

    val brandColor = remember(detail.brandColor) {
        try { Color(android.graphics.Color.parseColor(detail.brandColor)) } catch (_: Exception) { BrandBlue }
    }
    val posterStyle = remember(detail.providerName) { PosterStyle.forProvider(detail.providerName) }
    val backdropDims = ProviderSizes.detailPoster(posterStyle)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Fullscreen backdrop + gradient_btt_50_0 (alpha 0.8) + ic_main_overlay (alpha 0.1).
        if (detail.poster.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(detail.poster)
                    .size(backdropDims.width.value.toInt() * 2, backdropDims.height.value.toInt() * 2)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.9f }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.8f)
                            ),
                            startY = context.resources.displayMetrics.heightPixels * 0.35f,
                            endY = context.resources.displayMetrics.heightPixels.toFloat()
                        )
                    )
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)))
        }

        // Floating back button.
        Surface(
            onClick = onBackClick,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.05f),
                focusedContainerColor = Color.White,
                contentColor = Color.White,
                focusedContentColor = Color.Black
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                TvText("‹", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Bottom-anchored stack: title -> actions -> description -> recommended -> seasons.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = YouTv.startLine, end = 96.dp, bottom = 24.dp)
        ) {
            // WidgetVideoDescription: title + rating pill + delivery meta (width 700dp).
            Column(modifier = Modifier.width(700.dp)) {
                TvText(
                    text = detail.title,
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
                    detail.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { rating ->
                        Box(
                            modifier = Modifier
                                .background(brandColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            TvText(
                                text = if (rating.contains("IMDb", ignoreCase = true)) rating else "IMDb $rating",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = Montserrat
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    val meta = buildList {
                        add(if (detail.seasons.isNullOrEmpty()) "Фільм" else "Серіал")
                        detail.year?.let { add(it.toString()) }
                        detail.genres.take(2).let { addAll(it) }
                    }.joinToString(" • ")
                    if (meta.isNotEmpty()) {
                        TvText(
                            text = meta,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = Montserrat,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ContentCardMainRow: play + seek hint + favorite, marginBottom 116dp zone.
            ActionsRow(
                brandColor = brandColor,
                watchPercent = watchPercent,
                isInWatchlist = isInWatchlist,
                isResolving = launchState is ua.ukrtv.app.domain.model.MediaLaunchState.Resolving,
                onWatchClick = onWatchClick,
                onToggleWatchlist = onToggleWatchlist
            )

            Spacer(Modifier.height(20.dp))

            // ContentCardDescriptionRow: guideline 70% — description box | voiceover column.
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                var descFocused by remember { mutableStateOf(false) }
                // square_white_selector: 2dp white border while focused, radius 8, padding 8.
                Box(
                    modifier = Modifier
                        .weight(0.7f)
                        .onFocusChanged { descFocused = it.isFocused }
                        .border(
                            width = if (descFocused) 2.dp else 0.dp,
                            color = if (descFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    val parts = remember(detail.description) { splitLeadParagraph(detail.description) }
                    Column {
                        if (parts.first.isNotBlank()) {
                            TvText(
                                text = parts.first,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Montserrat
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (parts.second.isNotBlank()) {
                            TvText(
                                text = parts.second,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                fontFamily = Montserrat,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(0.3f).padding(start = 16.dp, end = 12.dp)) {
                    TvText(
                        text = "ОЗВУЧКА",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Montserrat
                    )
                    Spacer(Modifier.height(6.dp))
                    val voiceovers = remember(detail.seasons) {
                        detail.seasons.orEmpty().flatMap { s -> s.voiceoverOptions }
                            .filter { it.isNotBlank() }
                            .distinct()
                    }
                    TvText(
                        text = if (voiceovers.isEmpty()) "—" else voiceovers.joinToString(" · "),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        fontFamily = Montserrat,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (related.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                RelatedSection(
                    related = related,
                    brandColor = brandColor,
                    providerHint = detail.providerName,
                    onMovieClick = onMovieClick
                )
            }

            if (!detail.seasons.isNullOrEmpty()) {
                Spacer(Modifier.height(12.dp))
                SeasonEpisodePicker(
                    seasons = detail.seasons,
                    onEpisodeClick = onEpisodeClick,
                    accentColor = brandColor
                )
            }
        }
    }
}

/** First paragraph bold (YouTV renders lead lines bold), remainder regular. */
private fun splitLeadParagraph(description: String): Pair<String, String> {
    val trimmed = description.trim()
    if (trimmed.isEmpty()) return "" to ""
    val idx = trimmed.indexOf('\n')
    if (idx > 0 && idx < 200) return trimmed.substring(0, idx).trim() to trimmed.substring(idx + 1).trim()
    val dot = trimmed.indexOf(". ")
    return if (dot in 40..220) {
        trimmed.substring(0, dot + 1) to trimmed.substring(dot + 1).trim()
    } else {
        trimmed to ""
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionsRow(
    brandColor: Color,
    watchPercent: Int,
    isInWatchlist: Boolean,
    isResolving: Boolean,
    onWatchClick: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Column {
            // Play 210x54, bg_button_selector: #66000000 -> brand fill, white bold text always.
            PillButton(
                onClick = onWatchClick,
                brandColor = brandColor,
                focusRequester = playFocusRequester,
                modifier = Modifier.size(width = YouTv.playBtnW, height = YouTv.buttonH)
            ) {
                if (isResolving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    TvText("ЗАВАНТАЖЕННЯ...", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = Montserrat)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xB3FFFFFF), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    TvText("ДИВИТИСЯ", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = Montserrat)
                }
            }
            // SeekBar 12dp under play (margins 14/4/14) + centered hint 14sp.
            if (watchPercent > 0 && !isResolving) {
                Spacer(Modifier.height(4.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(YouTv.playBtnW)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(watchPercent / 100f)
                                .fillMaxSize()
                                .background(brandColor, RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    TvText(
                        text = "Переглянуто $watchPercent%",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontFamily = Montserrat
                    )
                }
            }
        }

        // Favorite 64x54 bookmark button.
        PillButton(
            onClick = onToggleWatchlist,
            brandColor = brandColor,
            modifier = Modifier.size(width = YouTv.bookmarkW, height = YouTv.buttonH)
        ) {
            Icon(
                imageVector = if (isInWatchlist) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint = Color(0xB3FFFFFF),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** bg_button_selector pill: translucent dark idle, brand fill on focus, white content both states. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PillButton(
    onClick: () -> Unit,
    brandColor: Color,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(YouTv.buttonRadius)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x66000000),
            focusedContainerColor = brandColor,
            contentColor = Color.White,
            focusedContentColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RelatedSection(
    related: List<Movie>,
    brandColor: Color,
    providerHint: String?,
    onMovieClick: (Movie) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val posterStyle = remember(providerHint) { PosterStyle.forProvider(providerHint ?: related.firstOrNull()?.provider) }
    val dims = ProviderSizes.compactCard(posterStyle)

    // Collapsed: 48dp strip, fully transparent; expands + fades in when focused.
    val targetHeight = if (expanded) dims.height + 40.dp else YouTv.recommendedCollapsedH
    val targetAlpha by animateFloatAsState(if (expanded) 1f else 0f, tween(250), label = "relatedAlpha")
    val animatedHeight by animateDpAsState(targetValue = targetHeight, animationSpec = tween(250), label = "relatedHeight")
    Column(modifier = Modifier.fillMaxWidth()) {
        TvText(
            text = "Рекомендуємо",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Montserrat,
            modifier = Modifier.alpha(targetAlpha)
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight)
                .clipToBounds()
                .onFocusChanged { expanded = it.hasFocus }
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().alpha(targetAlpha)
            ) {
                itemsIndexed(related, key = { _, m -> m.pageUrl }) { _, movie ->
                    MovieCard(
                        movie = movie,
                        brandColor = brandColor,
                        accentColor = brandColor,
                        width = dims.width,
                        height = dims.height,
                        showFocusPanel = false,
                        onClick = { onMovieClick(movie) },
                        modifier = Modifier
                    )
                }
            }
        }
    }
}
