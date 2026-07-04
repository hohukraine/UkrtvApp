package ua.ukrtv.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.ui.theme.Gold
import ua.ukrtv.app.ui.theme.GridDefaults
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.OnSurfaceVariant
import ua.ukrtv.app.util.DeviceClass

@Composable
fun FocusInfoPanel(
    movie: Movie,
    modifier: Modifier = Modifier,
    brandColor: Color = BrandBlue
) {
    val deviceClass = LocalDeviceClass.current
    val canAnimate = deviceClass != DeviceClass.LOW

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp) // Floating effect
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = GridDefaults.horizontalPadding)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(brandColor.copy(alpha = 0.05f), Color.Transparent),
                    )
                )
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            AnimatedContent(
                targetState = movie.pageUrl,
                transitionSpec = {
                    if (canAnimate) {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    } else {
                        fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
                    }
                },
                label = "focusInfoContent"
            ) {
                Column {
                    Text(
                        text = movie.title,
                        color = OnSurface,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!movie.rating.isNullOrEmpty()) {
                            Text(
                                text = "★ ${movie.rating}",
                                color = Gold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (movie.year != null) {
                            Text(
                                text = movie.year.toString(),
                                color = OnSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }

                        movie.contentType?.let {
                            Text(
                                text = it,
                                color = brandColor.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 2.sp
                            )
                        }

                        if (movie.season != null && movie.episode != null) {
                            Text(
                                text = "S${movie.season} E${movie.episode}",
                                color = brandColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
