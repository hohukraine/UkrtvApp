package ua.ukrtv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.ui.home.components.TopBar
import ua.ukrtv.app.ui.home.components.ContentRow
import ua.ukrtv.app.ui.home.components.HeroBanner

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onMovieClick: (Movie) -> Unit,
    onSearchClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val onDismissItem: (Movie) -> Unit = { viewModel.dismissContinueWatching(it) }
    val searchFocusRequester = remember { FocusRequester() }
    var isFirstLoad by rememberSaveable { mutableStateOf(true) }
    var showBanner by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is HomeState.Success && isFirstLoad) {
            try {
                searchFocusRequester.requestFocus()
                isFirstLoad = false
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        // Defer banner image to post-first-frame
        withFrameNanos { }
        val elapsed = (System.nanoTime() - ua.ukrtv.app.UkrtvApplication.appStartTime) / 1_000_000
        android.util.Log.d("StartupTimer", "First frame at ${elapsed}ms from app start")
        showBanner = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        when (val s = state) {
            is HomeState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(s.brandColor)
                )
            }

            is HomeState.Success -> {
                val brandColor = Color(s.brandColor)

                Column {
                    TopBar(
                        brandColor = brandColor,
                        providers = s.providers,
                        currentProviderId = s.currentProviderId,
                        onSearchClick = onSearchClick,
                        onProviderClick = viewModel::switchProvider,
                        searchFocusRequester = searchFocusRequester
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 48.dp)
                    ) {
                        if (s.bannerMovie != null) {
                            if (showBanner) {
                                HeroBanner(
                                    item = s.bannerMovie,
                                    brandColor = brandColor,
                                    onWatchClick = { onMovieClick(s.bannerMovie) }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(320.dp)
                                        .background(Color(0xFF1A1A1A))
                                )
                            }
                        }

                        s.sections.forEach { section ->
                            ContentRow(
                                title = section.title,
                                items = section.items,
                                brandColor = brandColor,
                                onItemClick = onMovieClick,
                                onItemDismiss = if (section.title == "Продовжити перегляд") onDismissItem else null
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
