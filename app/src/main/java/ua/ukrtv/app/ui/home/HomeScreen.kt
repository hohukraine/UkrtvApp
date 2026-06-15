package ua.ukrtv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.Provider

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import ua.ukrtv.app.ui.home.MovieCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onMovieClick: (Movie) -> Unit,
    onSearchClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val backgroundColor = remember { Color(0xFF0F0F0F) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        when (val s = state) {
            is HomeState.Loading -> {
                val brandColor = remember(s.brandColor) { Color(s.brandColor) }
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = brandColor
                )
            }

            is HomeState.Success -> {
                val brandColor = remember(s.brandColor) { Color(s.brandColor) }
                Column {
                    TopBar(
                        brandColor = brandColor,
                        providers = s.providers,
                        currentProviderId = s.currentProviderId,
                        onSearchClick = onSearchClick,
                        onProviderClick = { viewModel.switchProvider(it) }
                    )


                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 48.dp)
                    ) {
                        if (s.bannerMovie != null) {
                            item(key = "hero") {
                                HeroBanner(
                                    item = s.bannerMovie,
                                    brandColor = brandColor,
                                    onWatchClick = { onMovieClick(s.bannerMovie) }
                                )
                            }
                        }

                        items(s.sections, key = { it.title }) { section ->

                            ContentRow(
                                title = section.title,
                                items = section.items,
                                brandColor = brandColor,
                                onItemClick = onMovieClick
                            )
                        }
                    }
                }
            }

            is HomeState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = Color.Red)
                }
            }
        }
    }
}

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
            .crossfade(true)
            .build()
    }

    val backgroundColor = remember { Color(0xFF0F0F0F) }
    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color(0xFF0F0F0F)),
            startY = 400f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp) // Трохи збільшимо висоту для солідності
    ) {
        // Фоновий постер
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.6f
        )

        // Градієнт для плавного переходу до фону застосунку
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 58.dp, bottom = 48.dp)
                .width(600.dp)
        ) {
            // Секція з "блогером"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                AsyncImage(
                    model = "https://yt3.googleusercontent.com/ytc/AIdro_mY_M1_U_X_X_X_X_X_X_X_X_X_X_X_X_X=s176-c-k-c0x00ffffff-no-rj", // Замініть на реальне фото блогера
                    contentDescription = "Blogger",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    val bloggerTextColor = remember { Color.White.copy(alpha = 0.6f) }
                    Text(
                        text = "ВИБІР БЛОГЕРА",
                        color = brandColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "ТОП 200 ФІЛЬМІВ",
                        color = bloggerTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val typeTextColor = remember { Color.White.copy(alpha = 0.5f) }
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
                Spacer(modifier = Modifier.height(16.dp))
                val descriptionTextColor = remember { Color.White.copy(alpha = 0.7f) }
                Text(
                    text = item.shortDescription,
                    color = descriptionTextColor,
                    fontSize = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TopBar(
    brandColor: Color,
    providers: List<Provider>,
    currentProviderId: String,
    onSearchClick: () -> Unit,
    onProviderClick: (String) -> Unit
) {
    val searchSurfaceScale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
    val searchSurfaceColors = ClickableSurfaceDefaults.colors(
        containerColor = Color(0xFF1E3A8A),
        focusedContainerColor = Color.White,
        contentColor = Color.White,
        focusedContentColor = Color.Black
    )
    val searchSurfaceShape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50))

    val providerSurfaceScale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
    val providerSurfaceShape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp))
    val providerUnselectedColor = remember { Color.White.copy(alpha = 0.1f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 58.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Search Button
            androidx.tv.material3.Surface(
                onClick = onSearchClick,
                scale = searchSurfaceScale,
                colors = searchSurfaceColors,
                shape = searchSurfaceShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(32.dp))

            // Provider Switcher
            providers.forEach { provider ->
                val isSelected = provider.id == currentProviderId
                
                androidx.tv.material3.Surface(
                    onClick = { onProviderClick(provider.id) },
                    scale = providerSurfaceScale,
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) brandColor else providerUnselectedColor,
                        focusedContainerColor = Color.White,
                        contentColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    shape = providerSurfaceShape,
                    modifier = Modifier
                        .height(32.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = provider.name.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // App Logo
        Text(
            text = "UKR TV",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )
    }
}

@Composable
fun ContentRow(
    title: String,
    items: List<Movie>,
    brandColor: Color,
    onItemClick: (Movie) -> Unit
) {
    val titleColor = remember(brandColor) { brandColor.copy(alpha = 0.7f) }

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = title.uppercase(),
            color = titleColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(start = 58.dp, bottom = 16.dp, top = 24.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 58.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items, key = { it.id + it.pageUrl }) { item ->
                MovieCard(
                    movie = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}
