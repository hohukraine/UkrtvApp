package ua.ukrtv.app.ui.top200

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.Gold
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.OnSurfaceDim
import ua.ukrtv.app.ui.theme.OnSurfaceVariant
import ua.ukrtv.app.ui.theme.OverlayLight
import ua.ukrtv.app.ui.theme.SurfaceFocus
import ua.ukrtv.app.ui.theme.SurfaceVariant
import ua.ukrtv.app.ui.theme.deviceImage
import ua.ukrtv.app.ui.components.RatingCircle
import ua.ukrtv.app.domain.model.Top200Movie
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.data.repository.Top200Repository
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class Top200ViewModel @Inject constructor(
    private val repository: Top200Repository
) : ViewModel() {
    val movies = repository.getTop200()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Top200Screen(
    viewModel: Top200ViewModel = hiltViewModel(),
    onMovieClick: (Top200Movie) -> Unit,
    onBack: () -> Unit
) {
    var isBackReady by remember { mutableStateOf(false) }
    val listFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(600)
        isBackReady = true
        withFrameNanos { }
        listFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 58.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { if (isBackReady) onBack() },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = SurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = OnSurface,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "ТОП 200 від Чесного Огляду",
                    color = Gold,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Щ", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "За версією каналу Що подивитися",
                        color = OnSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 48.dp),
            modifier = Modifier.focusRequester(listFocusRequester)
        ) {
            items(viewModel.movies, key = { it.rank }) { movie ->
                Surface(
                    onClick = { onMovieClick(movie) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = OverlayLight,
                        focusedContainerColor = SurfaceFocus
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val context = LocalContext.current
                    val deviceClass = LocalDeviceClass.current
                    val isMediatek = LocalIsMediatek.current
                    val (iw, ih) = when (deviceClass) {
                        DeviceClass.LOW -> 120 to 180
                        DeviceClass.MID -> 180 to 270
                        DeviceClass.HIGH -> 300 to 450
                    }
                    val imageRequest = remember(movie.posterUrl, deviceClass) {
                        ImageRequest.Builder(context)
                            .data(movie.posterUrl.ifEmpty { null })
                            .size(iw, ih)
                            .deviceImage(deviceClass, isMediatek)
                            .build()
                    }

                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Poster with rank badge
                        Box(modifier = Modifier.width(90.dp).height(135.dp)) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = movie.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                placeholder = ColorPainter(OverlayLight),
                                error = ColorPainter(OverlayLight)
                            )
                            
                            // Small Gold Rank Badge on poster
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .background(Gold, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("#${movie.rank}", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(20.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = movie.title,
                                    color = OnSurface,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (movie.year.isNotEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "(${movie.year})",
                                        color = OnSurfaceVariant,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            
                            if (movie.originalTitle.isNotEmpty() && movie.originalTitle != movie.title) {
                                Text(
                                    text = movie.originalTitle,
                                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            Text(
                                text = movie.comment,
                                color = OnSurfaceDim,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 20.sp
                            )
                        }
                        
                        // Rating circle if available
                        if (movie.rating > 0) {
                            Spacer(modifier = Modifier.width(16.dp))
                            RatingCircle(rating = movie.rating)
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                    }
                }
            }
        }
    }
}
