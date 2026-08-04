package ua.ukrtv.app.ui.player

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import coil3.request.ImageRequest
import ua.ukrtv.app.ui.theme.BrandBlue

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun EpisodeLoadingOverlay(
    id: String,
    poster: String,
    season: Int?,
    episode: Int?,
    visible: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "loadingAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (poster.isNotBlank()) {
                val sharedModifier = if (sharedTransitionScope != null && animatedContentScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "movie_poster_$id"),
                            animatedVisibilityScope = animatedContentScope
                        )
                    }
                } else Modifier

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(poster)
                        .crossfade(200)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .width(200.dp)
                        .height(280.dp)
                        .then(sharedModifier)
                        .clip(RoundedCornerShape(12.dp))
                        .graphicsLayer {
                            scaleX = 0.95f
                            scaleY = 0.95f
                        },
                    contentScale = ContentScale.Crop
                )
            }
            if (season != null && episode != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Сезон $season, Серія $episode",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = BrandBlue,
                strokeWidth = 2.5.dp
            )
        }
    }
}
