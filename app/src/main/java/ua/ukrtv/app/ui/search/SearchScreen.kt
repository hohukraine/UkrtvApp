package ua.ukrtv.app.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import ua.ukrtv.app.ui.home.components.HomeBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.data.repository.ContentRepository
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.ui.home.MovieCard
import ua.ukrtv.app.ui.theme.PlaceholderDark
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.Error
import ua.ukrtv.app.ui.theme.GridDefaults
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.OnSurfaceDim
import ua.ukrtv.app.ui.theme.OnSurfaceVariant
import ua.ukrtv.app.ui.theme.OverlayLight
import ua.ukrtv.app.ui.theme.Surface
import ua.ukrtv.app.ui.theme.SurfaceFocus
import ua.ukrtv.app.ui.theme.SurfaceVariant
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.LocalFormFactor
import ua.ukrtv.app.ui.theme.PhoneCardDefaults
import ua.ukrtv.app.ui.theme.PhoneGridDefaults
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.ukrtv.app.ui.theme.Shapes

import javax.inject.Inject

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<Movie>) : SearchState()
    data class Error(val message: String) : SearchState()
}

data class Suggestion(val text: String, val type: SuggestionType)

enum class SuggestionType { HISTORY, TRENDING }

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val historyDao: ua.ukrtv.app.data.local.dao.SearchHistoryDao,
    private val providerManager: ProviderManager,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state

    private val initialQ = try {
        java.net.URLDecoder.decode(savedStateHandle.get<String>("q") ?: "", "UTF-8")
    } catch (_: Exception) {
        savedStateHandle.get<String>("q") ?: ""
    }
    private val _query = MutableStateFlow(initialQ)
    val query: StateFlow<String> = _query

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history

    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = _suggestions

    val trendingMovies = flow {
        emit(repository.getPopularByCategory(ua.ukrtv.app.data.providers.ContentCategory.MOVIES).firstOrNull() ?: emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val brandColor: StateFlow<Long> = providerManager.activeProvider
        .map { provider ->
            val colorInt = try { android.graphics.Color.parseColor(provider.brandColor) } catch (_: Exception) { 0xFF6E85B7.toInt() }
            (colorInt.toLong() and 0xFFFFFFFFL)
        }
        .distinctUntilChanged()
        .onStart {
            val p = providerManager.activeProvider.value
            val colorInt = try { android.graphics.Color.parseColor(p.brandColor) } catch (_: Exception) { 0xFF6E85B7.toInt() }
            emit((colorInt.toLong() and 0xFFFFFFFFL))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0xFF6E85B7)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentProviderId: StateFlow<String> = providerManager.activeProvider
        .map { it.name }
        .distinctUntilChanged()
        .onStart { emit(providerManager.activeProvider.value.name) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        loadHistory()
        viewModelScope.launch {
            _query
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _state.value = SearchState.Idle
                        updateSuggestions("")
                        return@collectLatest
                    }

                    _state.value = SearchState.Loading
                    repository.search(query).collect { result ->
                        result.onSuccess { movies ->
                            _state.value = SearchState.Success(movies)
                        }.onFailure { e ->
                            _state.value = SearchState.Error(e.message ?: "Помилка пошуку")
                        }
                    }
                }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val recent = historyDao.getRecent().map { it.query }
            _history.value = recent
        }
    }

    fun saveToHistory(query: String) {
        if (query.length < 3) return
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.insert(ua.ukrtv.app.data.local.entity.SearchHistoryEntity(query.lowercase().trim()))
            loadHistory()
        }
    }

    fun retrySearch() {
        val q = _query.value
        if (q.isNotBlank()) {
            _query.value = ""
            _query.value = q
        }
    }

    fun search(query: String) {
        _query.value = query
    }

    fun updateQuery(q: String) {
        _query.value = q
        updateSuggestions(q)
    }

    private fun updateSuggestions(q: String) {
        val hist = _history.value
        if (q.isBlank()) {
            _suggestions.value = hist.take(5).map { Suggestion(it, SuggestionType.HISTORY) }
        } else {
            val ql = q.lowercase()
            val filtered = hist.filter { it.lowercase().contains(ql) }
                .take(5)
                .map { Suggestion(it, SuggestionType.HISTORY) }
            _suggestions.value = filtered
        }
    }

    fun clearQuery() {
        _query.value = ""
        _state.value = SearchState.Idle
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onMovieClick: (Movie) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val searchBarFocusRequester = remember { FocusRequester() }

    val brandColorLong by viewModel.brandColor.collectAsStateWithLifecycle()
    val providerColor = remember(brandColorLong) { Color(brandColorLong) }
    val formFactor = LocalFormFactor.current
    val isPhone = formFactor == FormFactor.PHONE

    // Entrance animations
    var trendingShown by remember { mutableStateOf(false) }
    var resultsShown by remember { mutableStateOf(false) }

    val trendingAlpha by animateFloatAsState(
        targetValue = if (trendingShown) 1f else 0f,
        animationSpec = tween(300),
        label = "trendingAlpha"
    )
    val resultsAlpha by animateFloatAsState(
        targetValue = if (resultsShown) 1f else 0f,
        animationSpec = tween(250),
        label = "resultsAlpha"
    )

    HomeBackground(
        brandColor = providerColor,
        focusedColor = providerColor,
        scrollFraction = { 0f },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isPhone) PhoneGridDefaults.horizontalPadding else GridDefaults.horizontalPadding, vertical = 24.dp)
        ) {
        LaunchedEffect(Unit) {
            searchBarFocusRequester.requestFocus()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = providerColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))

            androidx.tv.material3.Surface(
                onClick = { focusRequester.requestFocus(); keyboardController?.show() },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchBarFocusRequester),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = OverlayLight,
                    focusedContainerColor = SurfaceFocus,
                    contentColor = OnSurface,
                    focusedContentColor = OnSurface
                )
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { viewModel.updateQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            isFocused = state.isFocused
                        },
                    textStyle = TextStyle(
                        color = OnSurface,
                        fontSize = 18.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(providerColor),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            val q = query.trim()
                            if (q.length >= 3) {
                                viewModel.saveToHistory(q)
                                viewModel.search(q)
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Введіть назву фільму або серіалу...",
                                color = OnSurfaceDim,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                androidx.tv.material3.Surface(
                    onClick = { viewModel.clearQuery() },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = providerColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Очистити",
                        tint = OnSurface,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
            }

        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                is SearchState.Idle -> {
                    if (query.isNotEmpty() && suggestions.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "ПІДКАЗКИ",
                                color = OnSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            suggestions.forEach { suggestion ->
                                Surface(
                                    onClick = {
                                        viewModel.search(suggestion.text)
                                        keyboardController?.hide()
                                    },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Surface,
                                        focusedContainerColor = providerColor.copy(alpha = 0.2f),
                                        contentColor = OnSurface,
                                        focusedContentColor = OnSurface
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        val icon = when (suggestion.type) {
                                            SuggestionType.HISTORY -> Icons.Default.Search
                                            SuggestionType.TRENDING -> Icons.Default.Mic
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = providerColor.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            suggestion.text,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val trending by viewModel.trendingMovies.collectAsStateWithLifecycle()
                        LaunchedEffect(trending) {
                            if (trending.isNotEmpty()) {
                                delay(30)
                                trendingShown = true
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            if (history.isNotEmpty()) {
                                item(key = "history_label") {
                                    Text(
                                        "ОСТАННІ ЗАПИТИ",
                                        color = OnSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                }
                                item(key = "history_row") {
                                    LazyRow(
                                        modifier = Modifier
                                            .padding(bottom = 32.dp)
                                            .fillMaxWidth()
                                            .focusProperties {
                                                @Suppress("DEPRECATION")
                                                @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                                                enter = { searchBarFocusRequester }
                                                @Suppress("DEPRECATION")
                                                @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                                                exit = {
                                                    if (it == FocusDirection.Up) searchBarFocusRequester
                                                    else FocusRequester.Default
                                                }
                                            },
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp)
                                    ) {
                                        items(history, key = { it }) { item ->
                                            val onClick = remember(item) {
                                                {
                                                    viewModel.search(item)
                                                    keyboardController?.hide()
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Color(0xFF2A2A2A))
                                                    .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(50))
                                                    .clickable { onClick() }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    item,
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (trending.isNotEmpty()) {
                                item(key = "trending_label") {
                                    Text(
                                        "ПОПУЛЯРНЕ ЗАРАЗ",
                                        color = OnSurface,
                                        fontSize = if (isPhone) 11.sp else 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        modifier = Modifier
                                            .graphicsLayer { alpha = trendingAlpha }
                                            .padding(bottom = if (isPhone) 12.dp else 16.dp)
                                    )
                                }

                                val columns = if (isPhone) PhoneGridDefaults.columns else GridDefaults.columns
                                val cardWidth = if (isPhone) PhoneCardDefaults.posterWidth else CardDefaults.posterWidth
                                val cardHeight = if (isPhone) PhoneCardDefaults.posterHeight else CardDefaults.posterHeight
                                val colSpacing = if (isPhone) PhoneGridDefaults.columnSpacing else GridDefaults.columnSpacing
                                val rowSpacing = if (isPhone) PhoneGridDefaults.rowSpacing else GridDefaults.rowSpacing

                                val rows = trending.chunked(columns)
                                items(rows.size) { rowIndex ->
                                    val rowItems = rows[rowIndex]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer { alpha = trendingAlpha }
                                            .padding(bottom = rowSpacing),
                                        horizontalArrangement = Arrangement.spacedBy(colSpacing)
                                    ) {
                                        rowItems.forEach { movie ->
                                            val onClick = remember(movie.id) {
                                                {
                                                    viewModel.saveToHistory(movie.title)
                                                    onMovieClick(movie)
                                                }
                                            }
                                            MovieCard(
                                                movie = movie,
                                                width = cardWidth,
                                                height = cardHeight,
                                                onClick = onClick
                                            )
                                        }
                                    }
                                }
                            }

                            if (history.isEmpty() && trending.isEmpty()) {
                                item(key = "empty_state") {
                                    Text(
                                        "Почніть вводити назву для пошуку",
                                        color = OnSurfaceDim
                                    )
                                }
                            }
                        }
                    }
                }
                is SearchState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = providerColor
                    )
                }
                is SearchState.Success -> {
                    if (s.results.isNotEmpty()) {
                        LaunchedEffect(Unit) {
                            resultsShown = true
                        }
                        if (isPhone) {
                            LazyColumn(
                                modifier = Modifier.graphicsLayer { alpha = resultsAlpha },
                                contentPadding = PaddingValues(bottom = 32.dp)
                            ) {
                                items(s.results, key = { it.id + it.pageUrl }, contentType = { "movie" }) { movie ->
                                    val onClick = remember(movie.id) {
                                        {
                                            viewModel.saveToHistory(query)
                                            onMovieClick(movie)
                                        }
                                    }
                                    SearchRow(
                                        movie = movie,
                                        onClick = onClick
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                modifier = Modifier.graphicsLayer { alpha = resultsAlpha },
                                columns = GridCells.Fixed(GridDefaults.columns),
                                horizontalArrangement = Arrangement.spacedBy(GridDefaults.columnSpacing),
                                verticalArrangement = Arrangement.spacedBy(GridDefaults.rowSpacing),
                                contentPadding = PaddingValues(bottom = 32.dp)
                            ) {
                                items(s.results, key = { it.id + it.pageUrl }, contentType = { "movie" }) { movie ->
                                    val onClick = remember(movie.id) {
                                        {
                                            viewModel.saveToHistory(query)
                                            onMovieClick(movie)
                                        }
                                    }
                                    MovieCard(
                                        movie = movie,
                                        width = CardDefaults.posterWidth,
                                        height = CardDefaults.posterHeight,
                                        onClick = onClick
                                    )
                                }
                            }
                        }
                    }
                }
                is SearchState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            s.message,
                            color = Error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.tv.material3.Button(onClick = { viewModel.retrySearch() }) {
                            Text("Повторити", color = Color.White)
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SearchRow(
    movie: Movie,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Poster thumbnail
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141414))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(movie.poster)
                    .crossfade(true)
                    .build(),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = PlaceholderDark,
                error = PlaceholderDark
            )
        }

        // Metadata
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = movie.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (movie.year != null) {
                    Text(
                        text = movie.year.toString(),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                if (!movie.rating.isNullOrEmpty()) {
                    Text(
                        text = "\u2605 ${movie.rating}",
                        color = Color(0xFFDAA520),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!movie.quality.isNullOrEmpty()) {
                    Text(
                        text = movie.quality.uppercase(),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                }
            }

            if (!movie.contentType.isNullOrEmpty()) {
                Text(
                    text = movie.contentType,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
