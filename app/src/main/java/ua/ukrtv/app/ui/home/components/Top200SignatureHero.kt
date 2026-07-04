package ua.ukrtv.app.ui.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.Gold
import ua.ukrtv.app.ui.theme.HeroDefaults
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.Shapes

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Top200SignatureHero(
    items: List<Top200Movie>,
    brandColor: Color,
    onItemClick: (Top200Movie) -> Unit,
    onItemLongClick: (Top200Movie) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HeroDefaults.height)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val movie = items[page]
            HeroPage(
                movie = movie, 
                brandColor = brandColor, 
                onClick = { onItemClick(movie) },
                onLongClick = { onItemLongClick(movie) }
            )
        }
        
        // Bottom Gradient Fade to Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeroDefaults.bottomFadeHeight)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Background)
                    )
                )
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroPage(
    movie: Top200Movie,
    brandColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = remember(movie.accentColor) {
        try { Color(android.graphics.Color.parseColor(movie.accentColor)) } catch (_: Exception) { Color(0xFF08121c) }
    }
    
    val onAccentColor = remember(accentColor) {
        val luminance = 0.299 * accentColor.red + 0.587 * accentColor.green + 0.114 * accentColor.blue
        if (luminance > 0.5) Color.Black else Color.White
    }
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if ((event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    val isLongPress = event.nativeKeyEvent.flags and android.view.KeyEvent.FLAG_LONG_PRESS != 0
                    if (isLongPress && event.type == KeyEventType.KeyDown) {
                        onLongClick()
                        return@onKeyEvent true
                    }
                    if (isLongPress) return@onKeyEvent true
                }
                false
            },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = accentColor,
            focusedContainerColor = accentColor
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // LAYER 1: Backdrop Image with Blur
            if (movie.backdropUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(movie.backdropUrl)
                        .crossfade(true)
                        .size(1280, 720)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(24.dp)
                        .alpha(0.5f)
                )
            }
            
            // LAYER 2: Immersive Gradient (TMDB Style)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(accentColor, accentColor.copy(alpha = 0.9f), Color.Transparent),
                            startX = 0f,
                            endX = 1600f
                        )
                    )
            )

            // LAYER 3: Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // POSTER
                Surface(
                    onClick = onClick,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier
                        .width(200.dp)
                        .height(300.dp)
                        .shadow(24.dp, RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, onAccentColor.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(8.dp)
                        )
                    )
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(movie.posterUrl)
                            .size(400, 600)
                            .build(),
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(48.dp))

                // METADATA
                Column(modifier = Modifier.weight(1f)) {
                    // Rank & Source
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ТОП 200 # ${movie.rank}".uppercase(),
                            color = if (onAccentColor == Color.Black) Color(0xFF997a00) else Gold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.width(16.dp))
                        // Channel Identity
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(onAccentColor.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Щ", color = onAccentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "ЩО ПОДИВИТИСЯ",
                                color = onAccentColor.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Title (Year)
                    Text(
                        text = buildAnnotatedString {
                            append(movie.title)
                            if (movie.year.isNotEmpty()) {
                                withStyle(SpanStyle(color = onAccentColor.copy(alpha = 0.6f), fontWeight = FontWeight.Normal)) {
                                    append(" (${movie.year})")
                                }
                            }
                        },
                        color = onAccentColor,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 48.sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // TMDB Style Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (movie.rating > 0) {
                            RatingCircle(rating = movie.rating, textColor = onAccentColor)
                        }
                        
                        val metadata = mutableListOf<String>()
                        if (movie.genres.isNotEmpty()) metadata.add(movie.genres.take(3).joinToString(", "))
                        if (movie.director.isNotEmpty()) metadata.add(movie.director)
                        
                        Text(
                            text = metadata.joinToString(" • "),
                            color = onAccentColor.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Channel Comment (Review)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(onAccentColor.copy(alpha = 0.05f))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "ОГЛЯД ВІД ЧЕСНОГО ОГЛЯДУ",
                                color = (if (onAccentColor == Color.Black) Color(0xFF997a00) else Gold).copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = movie.comment,
                                color = onAccentColor,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Button
                    Surface(
                        onClick = onClick,
                        shape = ClickableSurfaceDefaults.shape(Shapes.chip),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = onAccentColor,
                            contentColor = accentColor,
                            focusedContainerColor = if (onAccentColor == Color.Black) Color.DarkGray else Gold,
                            focusedContentColor = Color.Black
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ДИВИТИСЯ", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RatingCircle(rating: Int, textColor: Color = OnSurface) {
    val color = when {
        rating >= 70 -> Color(0xFF21d07a)
        rating >= 40 -> Color(0xFFd2d531)
        else -> Color(0xFFdb2360)
    }
    
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = color.copy(alpha = 0.2f), style = Stroke(width = 3.dp.toPx()))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = (rating / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text = rating.toString(),
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

