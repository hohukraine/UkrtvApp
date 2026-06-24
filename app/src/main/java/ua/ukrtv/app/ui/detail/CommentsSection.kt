package ua.ukrtv.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.domain.model.Comment

@Composable
fun CommentsSection(
    comments: List<Comment>,
    providerName: String,
    providerLogoUrl: String?,
    accentColor: Color = Color(0xFF6E85B7),
    modifier: Modifier = Modifier
) {
    if (comments.isEmpty()) return

    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(
                text = "КОМЕНТАРІ З",
                color = Color(0xFF7A7A7A),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.5.sp
            )
            if (!providerLogoUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                val logo = providerLogoUrl
                val logoRequest = remember(logo) {
                    ImageRequest.Builder(context)
                        .data(logo)
                        .size(160, 36)
                        .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = logoRequest,
                    contentDescription = providerName,
                    modifier = Modifier
                        .height(18.dp)
                        .widthIn(max = 80.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = providerName,
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp
                )
            }
        }

        val displayComments = remember(comments) { comments.take(15) }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            displayComments.forEachIndexed { idx, comment ->
                CommentCard(comment, accentColor)
            }
        }
    }
}

@Composable
private fun CommentCard(
    comment: Comment,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF151517), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (comment.avatar.isNotBlank()) {
            val ctx = LocalContext.current
            val avatarRequest = remember(comment.avatar) {
                ImageRequest.Builder(ctx)
                    .data(comment.avatar)
                    .size(72, 72)
                    .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = avatarRequest,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2D)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2D)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.author.firstOrNull()?.uppercase() ?: "?",
                    color = Color(0xFF7A7A7A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author,
                    color = accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (comment.date.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = comment.date,
                        color = Color(0xFF7A7A7A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = comment.text,
                color = Color(0xFFE1E1E1).copy(alpha = 0.7f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
