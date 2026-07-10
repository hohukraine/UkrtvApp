package ua.ukrtv.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import ua.ukrtv.app.ui.theme.PlaceholderDark
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.ui.theme.deviceImage
import ua.ukrtv.app.util.DeviceClass

private val cardShape = RoundedCornerShape(8.dp)

@Composable
fun MovieCard(
    movie: Movie,
    brandColor: Color = Color(0xFF6E85B7),
    width: Dp = CardDefaults.posterWidth,
    height: Dp = CardDefaults.posterHeight,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val actualDismiss = onLongClick ?: onDismiss
    val ctx = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val deviceClass = LocalDeviceClass.current
    val isMediatek = LocalIsMediatek.current

    val targetScale = when (deviceClass) {
        DeviceClass.LOW -> 1.05f
        DeviceClass.MID -> 1.08f
        DeviceClass.HIGH -> 1.1f
    }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) targetScale else 1f,
        animationSpec = tween(if (deviceClass == DeviceClass.HIGH) 400 else 300),
        label = "cardScale"
    )

    val translateY by animateFloatAsState(
        targetValue = if (isFocused && deviceClass == DeviceClass.HIGH) (-6f) else 0f,
        animationSpec = tween(400),
        label = "cardTranslateY"
    )

    val imageRequest = remember(movie.poster, deviceClass) {
        val (iw, ih) = when (deviceClass) {
            DeviceClass.LOW -> 120 to 180
            DeviceClass.MID -> 180 to 270
            DeviceClass.HIGH -> 300 to 450
        }
        ImageRequest.Builder(ctx)
            .data(movie.poster)
            .size(iw, ih)
            .deviceImage(deviceClass, isMediatek)
            .build()
    }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .scale(scale)
            .then(
                if (isFocused && deviceClass == DeviceClass.HIGH) {
                    Modifier.shadow(16.dp, cardShape, ambientColor = brandColor.copy(alpha = 0.6f), spotColor = brandColor.copy(alpha = 0.4f))
                } else if (isFocused && deviceClass == DeviceClass.MID) {
                    Modifier.shadow(8.dp, cardShape, ambientColor = brandColor.copy(alpha = 0.3f), spotColor = brandColor.copy(alpha = 0.2f))
                } else Modifier
            )
            .clip(cardShape)
            .background(Color(0xFF141414))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .onKeyEvent { event ->
                if (actualDismiss != null) {
                    val isMenu = event.key == Key.Menu || event.key == Key.Settings
                    if (isMenu && event.type == KeyEventType.KeyUp) {
                        actualDismiss()
                        return@onKeyEvent true
                    }
                }
                false
            }
            .then(
                if (isFocused) {
                    val borderColor = when {
                        deviceClass == DeviceClass.HIGH -> Color.White
                        deviceClass == DeviceClass.MID -> brandColor
                        else -> Color.White.copy(alpha = 0.8f)
                    }
                    val borderWidth = when (deviceClass) {
                        DeviceClass.HIGH -> 3.dp
                        DeviceClass.MID -> 2.dp
                        DeviceClass.LOW -> 2.dp
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

        if (movie.provider != null) {
            val providerColor = when (movie.provider) {
                "Uakino" -> Color(0xFFFF6B35)
                "Eneyida" -> Color(0xFF4ECDC4)
                else -> Color(0xFF888888)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(providerColor.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = movie.provider.uppercase(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 3.dp)
            ) {
                if (movie.year != null) {
                    Text(
                        text = movie.year.toString(),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!movie.rating.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "\u2605",
                            color = Color(0xFFDAA520),
                            fontSize = 9.sp
                        )
                        Spacer(Modifier.width(1.dp))
                        Text(
                            text = movie.rating,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!movie.quality.isNullOrEmpty()) {
                    Text(
                        text = movie.quality.uppercase(),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
