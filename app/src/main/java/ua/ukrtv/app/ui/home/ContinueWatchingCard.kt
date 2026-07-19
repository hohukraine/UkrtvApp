package ua.ukrtv.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ua.ukrtv.app.ui.theme.PlaceholderDark
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalFormFactor
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.Shapes
import ua.ukrtv.app.ui.theme.deviceImage
import ua.ukrtv.app.util.DeviceClass

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingCard(
    movie: Movie,
    brandColor: Color = Color(0xFF6E85B7),
    modifier: Modifier = Modifier,
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

    var deleteMode by remember { mutableStateOf(false) }

    val targetScale = when (deviceClass) {
        DeviceClass.LOW -> 1.05f
        DeviceClass.MID -> 1.08f
        DeviceClass.HIGH -> 1.1f
    }
    val scale by animateFloatAsState(
        targetValue = if (isFocused && !deleteMode) targetScale else 1f,
        animationSpec = tween(if (deviceClass == DeviceClass.LOW) 0 else if (deviceClass == DeviceClass.HIGH) 400 else 300),
        label = "cardScale"
    )

    val translateY by animateFloatAsState(
        targetValue = if (isFocused && !deleteMode && deviceClass == DeviceClass.HIGH) (-6f) else 0f,
        animationSpec = tween(400),
        label = "cardTranslateY"
    )

    val imageRequest = remember(movie.poster, deviceClass) {
        val (iw, ih) = when (deviceClass) {
            DeviceClass.LOW -> 180 to 270
            DeviceClass.MID -> 260 to 390
            DeviceClass.HIGH -> 400 to 600
        }
        ImageRequest.Builder(ctx)
            .data(movie.poster)
            .size(iw, ih)
            .deviceImage(deviceClass, isMediatek)
            .build()
    }

    val cardShape = remember { Shapes.cardCompact }

    val episodeLabel = remember(movie.season, movie.episode) {
        if (movie.season != null && movie.episode != null) {
            "S${movie.season} E${movie.episode}"
        } else null
    }

    val cardScale = remember(deviceClass) {
        when (deviceClass) {
            DeviceClass.LOW -> 0.75f
            DeviceClass.MID -> 1.0f
            DeviceClass.HIGH -> 1.25f
        }
    }

    Column(
        modifier = modifier.width(CardDefaults.compactWidth * cardScale)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CardDefaults.compactHeight * cardScale)
                .scale(scale)
                .then(
                    if (isFocused && !deleteMode && deviceClass == DeviceClass.HIGH) {
                        Modifier.shadow(16.dp, cardShape, ambientColor = brandColor.copy(alpha = 0.6f), spotColor = brandColor.copy(alpha = 0.4f))
                    } else if (isFocused && !deleteMode && deviceClass == DeviceClass.MID) {
                        Modifier.shadow(8.dp, cardShape, ambientColor = brandColor.copy(alpha = 0.3f), spotColor = brandColor.copy(alpha = 0.2f))
                    } else Modifier
                )
                .clip(cardShape)
                .background(Color(0xFF141414))
                .onFocusChanged { if (!it.isFocused) deleteMode = false }
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
                            deviceClass == DeviceClass.HIGH -> Color.White
                            deviceClass == DeviceClass.MID -> brandColor
                            else -> Color.White.copy(alpha = 0.8f)
                        }
                        val borderWidth = when (deviceClass) {
                            DeviceClass.HIGH -> 3.dp
                            else -> 2.dp
                        }
                        Modifier.border(BorderStroke(borderWidth, borderColor), cardShape)
                    } else Modifier
                )
                .offset(y = translateY.dp)
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = PlaceholderDark,
                error = PlaceholderDark
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = isFocused && !deleteMode && isTv,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(brandColor.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
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
                        color = brandColor.copy(alpha = 0.9f),
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
    }
}
