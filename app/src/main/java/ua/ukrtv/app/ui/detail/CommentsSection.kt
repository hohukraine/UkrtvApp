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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.domain.model.Comment

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CommentsSection(
    comments: List<Comment>,
    providerName: String,
    accentColor: Color = Color(0xFF6E85B7),
    modifier: Modifier = Modifier
) {
    if (comments.isEmpty()) return

    var showAll by remember { mutableStateOf(false) }
    val displayComments = remember(comments, showAll) {
        if (showAll) comments else comments.take(10)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            Text(
                text = "КОМЕНТАРІ",
                color = Color(0xFF7A7A7A),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "(${comments.size})",
                color = Color(0xFF4A4A4A),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = providerName,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            displayComments.forEach { comment ->
                CommentCard(comment = comment, accentColor = accentColor)
            }
        }

        if (comments.size > 10) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                onClick = { showAll = !showAll },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = accentColor.copy(alpha = 0.1f),
                    contentColor = accentColor,
                    focusedContentColor = accentColor
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                border = ClickableSurfaceDefaults.border(
                    border = androidx.tv.material3.Border(
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A2D))
                    ),
                    focusedBorder = androidx.tv.material3.Border(
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor)
                    )
                )
            ) {
                Text(
                    text = if (showAll) "ЗГОРНУТИ" else "ПОКАЗАТИ ВСІ (${comments.size})",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CommentCard(
    comment: Comment,
    accentColor: Color
) {
    Surface(
        onClick = {},
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF151517),
            focusedContainerColor = Color(0xFF1E1E20),
            contentColor = Color(0xFFE1E1E1),
            focusedContentColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A2D))
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A3A3D))
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2D)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2D)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = comment.author.firstOrNull()?.uppercase() ?: "?",
                        color = Color(0xFF7A7A7A),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

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
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = comment.date,
                            color = Color(0xFF5A5A5A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = comment.text,
                    color = Color(0xFFC0C0C0),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
