package ua.ukrtv.app.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.ContentType

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroBanner(
    item: Movie,
    brandColor: Color,
    onWatchClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(item.poster) {
        ImageRequest.Builder(context)
            .data(item.poster)
            .size(640, 360)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .crossfade(false)
            .build()
    }

    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0x990F0F0F), Color(0xFF0F0F0F)),
            startY = 260f
        )
    }

    val typeTextColor = remember { Color.White.copy(alpha = 0.5f) }
    val descriptionTextColor = remember { Color.White.copy(alpha = 0.7f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 58.dp, bottom = 32.dp)
                .width(600.dp)
        ) {
            Text(
                text = if (item.type == ContentType.MOVIE) "ФІЛЬМ" else "СЕРІАЛ",
                color = typeTextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                lineHeight = 54.sp
            )

            if (item.shortDescription != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = item.shortDescription,
                    color = descriptionTextColor,
                    fontSize = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val buttonShape = RoundedCornerShape(4.dp)
            val buttonScale = ButtonDefaults.scale(focusedScale = 1.05f)
            val buttonColors = ButtonDefaults.colors(
                containerColor = brandColor,
                focusedContainerColor = Color.White,
                contentColor = Color.White,
                focusedContentColor = Color.Black
            )

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Button(
                    onClick = onWatchClick,
                    shape = ButtonDefaults.shape(buttonShape),
                    scale = buttonScale,
                    colors = buttonColors,
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
                ) {
                    Text("▶  ДИВИТИСЯ ЗАРАЗ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
