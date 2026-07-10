package ua.ukrtv.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.OnSurfaceVariant

@Composable
fun MovieInfoHeader(
    movie: Movie?,
    isVisible: Boolean,
    brandColor: Color = BrandBlue,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && movie != null,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(400)),
        modifier = modifier.fillMaxWidth()
    ) {
        movie?.let { m ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Background)
            ) {
                // Glass edge highlight
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                        .align(Alignment.TopCenter)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(
                            horizontal = GridDefaults.horizontalPadding
                        ),
                    contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = m.title,
                        color = OnSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(16.dp))

                    if (!m.rating.isNullOrEmpty()) {
                        Text(
                            text = "★ ${m.rating}",
                            color = Gold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(16.dp))
                    }

                    if (m.year != null) {
                        Text(
                            text = m.year.toString(),
                            color = OnSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.width(16.dp))
                    }

                    m.contentType?.let {
                        Text(
                            text = it.uppercase(),
                            color = brandColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }

                    if (m.season != null && m.episode != null) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "S${m.season} E${m.episode}",
                            color = brandColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            }
        }
    }
}
