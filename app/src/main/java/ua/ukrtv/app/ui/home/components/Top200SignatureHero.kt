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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import ua.ukrtv.app.ui.components.RatingCircle
import ua.ukrtv.app.ui.theme.Gold
import ua.ukrtv.app.ui.theme.HeroDefaults
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.Shapes
import ua.ukrtv.app.ui.theme.deviceImage
import ua.ukrtv.app.util.DeviceClass

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Top200SignatureHero(
    items: List<Top200Movie>,
    brandColor: Color,
    onItemClick: (Top200Movie) -> Unit,
    onItemLongClick: (Top200Movie) -> Unit = {},
    onActiveMovieChange: ((Top200Movie) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    
    LaunchedEffect(pagerState.currentPage) {
        onActiveMovieChange?.invoke(items[pagerState.currentPage])
    }
    
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
    val deviceClass = LocalDeviceClass.current
    val isMediatek = LocalIsMediatek.current
    val accentColor = remember(movie.accentColor) {
        try { Color(android.graphics.Color.parseColor(movie.accentColor)) } catch (_: Exception) { Color(0xFF08121c) }
    }
    
    val onAccentColor = remember(accentColor) {
        val luminance = 0.299 * accentColor.red + 0.587 * accentColor.green + 0.114 * accentColor.blue
        if (luminance > 0.5) Color.Black else Color.White
    }
    
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .fillMaxSize(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Content
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
                    val (iw, ih) = when (deviceClass) { DeviceClass.LOW -> 200 to 300; DeviceClass.MID -> 400 to 600; DeviceClass.HIGH -> 600 to 900 }
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(movie.posterUrl)
                            .size(iw, ih)
                            .deviceImage(deviceClass, isMediatek)
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
                        Spacer(Modifier.width(20.dp))
                        // Channel Identity (Avatar + Name)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(onAccentColor.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(ua.ukrtv.app.R.drawable.avatar_chesnyi)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Чесний огляд",
                                color = onAccentColor.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Title (Year) - Adaptive Scaling
                    val titleLength = movie.title.length
                    val baseFontSize = if (deviceClass == DeviceClass.HIGH) 54.sp else 42.sp
                    val adaptiveFontSize = when {
                        titleLength > 45 -> baseFontSize * 0.65f
                        titleLength > 30 -> baseFontSize * 0.8f
                        else -> baseFontSize
                    }
                    val adaptiveLineHeight = adaptiveFontSize * 1.1f

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
                        fontSize = adaptiveFontSize,
                        lineHeight = adaptiveLineHeight,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

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
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Channel Comment (Review)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(onAccentColor.copy(alpha = 0.05f))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "«${movie.comment}»",
                            color = onAccentColor,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

