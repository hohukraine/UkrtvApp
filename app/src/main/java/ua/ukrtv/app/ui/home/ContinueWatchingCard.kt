package ua.ukrtv.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.PosterStyle
import ua.ukrtv.app.ui.theme.ProviderSizes
import ua.ukrtv.app.ui.theme.CardDimensions
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalFormFactor
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.PlaceholderDark
import ua.ukrtv.app.ui.theme.deviceImage
import ua.ukrtv.app.util.DeviceClass

private val cardRadius = 12.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingCard(
    movie: Movie,
    brandColor: Color = Color(0xFF6E85B7),
    accentColor: Color = brandColor,
    width: androidx.compose.ui.unit.Dp? = null,
    height: androidx.compose.ui.unit.Dp? = null,
    modifier: Modifier = Modifier,
    focusModifier: Modifier = Modifier,
    showFocusPanel: Boolean = false,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val formFactor = LocalFormFactor.current
    val isTv = formFactor == FormFactor.TV
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val deviceClass = LocalDeviceClass.current
    val isMediatek = LocalIsMediatek.current
    val density = LocalDensity.current.density

    var deleteMode by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused?.invoke()
        } else {
            deleteMode = false
        }
    }

    val targetScale = when (deviceClass) {
        DeviceClass.LOW -> 1.05f
        DeviceClass.MID -> 1.08f
        DeviceClass.HIGH -> 1.1f
    }
    val scale by animateFloatAsState(
        targetValue = if (isFocused && !deleteMode) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardScale"
    )

    val translateY by animateFloatAsState(
        targetValue = if (isFocused && !deleteMode && deviceClass == DeviceClass.HIGH) (-6f) else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardTranslateY"
    )

    val playIconAlpha by animateFloatAsState(
        targetValue = if (isFocused && !deleteMode && isTv) 1f else 0f,
        animationSpec = tween(200),
        label = "playIconAlpha"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isFocused || !isTv) 1f else 0f,
        animationSpec = tween(300),
        label = "contentAlpha"
    )

    val posterStyle = remember(movie.provider) {
        PosterStyle.forProvider(movie.provider)
    }
    val cardDims = remember(posterStyle, width, height) {
        if (width != null && height != null) {
            CardDimensions(width, height)
        } else {
            ProviderSizes.compactCard(posterStyle)
        }
    }

    val imageRequest = remember(movie.poster, deviceClass, posterStyle, cardDims) {
        if (movie.poster.isNullOrBlank()) return@remember null

        val styleForImage = if (width != null && height != null) {
            if (width > height) PosterStyle.WIDE else PosterStyle.VERTICAL
        } else posterStyle

        val (iw, ih) = when (styleForImage) {
            PosterStyle.WIDE -> when (deviceClass) {
                DeviceClass.LOW -> 240 to 135
                DeviceClass.MID -> 320 to 180
                DeviceClass.HIGH -> 480 to 270
            }
            PosterStyle.VERTICAL -> when (deviceClass) {
                DeviceClass.LOW -> 180 to 270
                DeviceClass.MID -> 240 to 360
                DeviceClass.HIGH -> 360 to 540
            }
        }
        ImageRequest.Builder(ctx)
            .data(movie.poster)
            .size(iw, ih)
            .deviceImage(deviceClass, isMediatek)
            .build()
    }

    val episodeLabel = remember(movie.season, movie.episode) {        if (movie.season != null && movie.episode != null) {
            "S${movie.season} E${movie.episode}"
        } else null
    }

    val cardScale = remember(deviceClass) {
        when (deviceClass) {
            DeviceClass.LOW -> 0.75f
            DeviceClass.MID -> 1.0f
            DeviceClass.HIGH -> 1.0f
        }
    }

    val showPanel = showFocusPanel && isTv
    val labelHeight = if (showPanel) CardDefaults.focusPanelHeight else 0.dp

    val panelProgress by animateFloatAsState(
        targetValue = if (isFocused && !deleteMode && showPanel) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessLow
        ),
        label = "panelProgress"
    )

    // Poster bottom corners square off while the label bar expands under it.
    val tileBottomRadius by animateDpAsState(
        targetValue = if (showPanel && isFocused && !deleteMode) 0.dp else cardRadius,
        animationSpec = tween(320),
        label = "tileBottomRadius"
    )
    val tileShape = RoundedCornerShape(
        topStart = cardRadius,
        topEnd = cardRadius,
        bottomStart = tileBottomRadius,
        bottomEnd = tileBottomRadius
    )

    Box(
        modifier = modifier
            .width(cardDims.width * (if (width != null) 1f else cardScale))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = translateY * density
            }
    ) {
        Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardDims.height * (if (height != null) 1f else cardScale))
                .graphicsLayer {
                    if (isFocused && !deleteMode) {
                        shadowElevation = if (deviceClass == DeviceClass.HIGH) 26.dp.toPx() else 10.dp.toPx()
                        spotShadowColor = accentColor.copy(alpha = if (deviceClass == DeviceClass.HIGH) 0.35f else 0.2f)
                        ambientShadowColor = accentColor.copy(alpha = if (deviceClass == DeviceClass.HIGH) 0.45f else 0.25f)
                    }
                    clip = true
                    shape = tileShape
                }
                .background(Color(0xFF141414))
                .then(focusModifier)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = if (formFactor == FormFactor.PHONE) ripple() else null,
                    onClick = { if (deleteMode) onDismiss?.invoke() else onClick() },
                    onLongClick = { if (!deleteMode) deleteMode = true }
                )
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp) {
                        if (event.key == Key.Back && deleteMode) {
                            deleteMode = false
                            return@onKeyEvent true
                        }
                        if (deleteMode && (event.key == Key.Menu || event.key == Key.Settings || event.key == Key.MediaSkipBackward)) {
                            return@onKeyEvent true
                        }
                        if (onDismiss != null && !deleteMode) {
                            val isMenu = event.key == Key.Menu || event.key == Key.Settings
                            val isDelete = event.key == Key.MediaSkipBackward
                            if (isMenu || isDelete) {
                                onDismiss()
                                return@onKeyEvent true
                            }
                        }
                    }
                    false
                }
                .then(
                    if (isFocused && !deleteMode) {
                        val borderColor = when {
                            deviceClass == DeviceClass.HIGH -> accentColor.copy(alpha = 0.6f)
                            deviceClass == DeviceClass.MID -> accentColor.copy(alpha = 0.9f)
                            else -> Color.White.copy(alpha = 0.8f)
                        }
                        Modifier.border(BorderStroke(2.dp, borderColor), tileShape)
                    } else Modifier
                )
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = PlaceholderDark,
                error = PlaceholderDark
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = playIconAlpha
                        val s = 0.8f + (playIconAlpha * 0.2f)
                        scaleX = s
                        scaleY = s
                    }
                    .size(40.dp)
                    .background(brandColor.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            if (showPanel) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                )
            }

            if (!showPanel) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, end = 10.dp, bottom = 6.dp)
                        .graphicsLayer { alpha = contentAlpha }
                ) {
                    Text(
                        text = movie.title.uppercase(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.3.sp,
                        lineHeight = 14.sp
                    )
                    if (episodeLabel != null) {
                        Text(
                            text = episodeLabel,
                            color = accentColor.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    if (!movie.duration.isNullOrEmpty()) {
                        Text(
                            text = movie.duration,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (deleteMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .semantics { contentDescription = "Видалити зі списку" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u2715",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showPanel) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(labelHeight)
                    .clip(
                        RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 0.dp,
                            bottomStart = cardRadius,
                            bottomEnd = cardRadius
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = (1f - panelProgress) * labelHeight.toPx()
                            alpha = panelProgress
                        }
                        .background(Color(0xFF0D0D0F))
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(accentColor.copy(alpha = 0.6f))
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = movie.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 0.2.sp,
                            lineHeight = 16.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 3.dp)
                        ) {
                            if (episodeLabel != null) {
                                Text(
                                    text = episodeLabel,
                                    color = accentColor.copy(alpha = 0.95f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            if (!movie.duration.isNullOrEmpty()) {
                                Text(
                                    text = movie.duration,
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
