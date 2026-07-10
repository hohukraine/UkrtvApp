package ua.ukrtv.app.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ua.ukrtv.app.ui.theme.LocalDeviceClass
import ua.ukrtv.app.ui.theme.LocalIsMediatek
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.Error
import ua.ukrtv.app.ui.theme.GridDefaults
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.OnSurfaceVariant
import ua.ukrtv.app.ui.theme.OverlayLight
import ua.ukrtv.app.ui.theme.Surface
import ua.ukrtv.app.ui.theme.SurfaceFocus
import ua.ukrtv.app.ui.theme.SurfaceVariant

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

    private val initialQ = savedStateHandle.get<String>("q") ?: ""
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
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val history by viewModel.history.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val brandColorLong by viewModel.brandColor.collectAsState()
    val providerColor = remember(brandColorLong) { Color(brandColorLong) }

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

    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }

    HomeBackground(
        brandColor = providerColor,
        focusedColor = providerColor,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = GridDefaults.horizontalPadding, vertical = 24.dp)
        ) {
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
                    .weight(1f),
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
                                color = OnSurfaceVariant,
                                fontSize = 18.sp
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
                                color = OnSurfaceVariant,
                                fontSize = 12.sp,
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
                        val trending by viewModel.trendingMovies.collectAsState()
                        LaunchedEffect(trending) {
                            if (trending.isNotEmpty()) {
                                delay(30)
                                trendingShown = true
                            }
                        }

                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            if (history.isNotEmpty()) {
                                Text(
                                    "ОСТАННІ ЗАПИТИ",
                                    color = OnSurfaceVariant,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                LazyRow(
                                    modifier = Modifier
                                        .padding(bottom = 32.dp)
                                        .fillMaxWidth()
                                        .focusProperties {
                                            @Suppress("DEPRECATION")
                                            @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                                            exit = { FocusRequester.Default }
                                        },
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    items(history) { item ->
                                        Surface(
                                            onClick = {
                                                viewModel.search(item)
                                                keyboardController?.hide()
                                            },
                                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = SurfaceVariant,
                                                contentColor = OnSurface
                                            )
                                        ) {
                                            Text(
                                                item,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }

                            if (trending.isNotEmpty()) {
                                Column(modifier = Modifier.graphicsLayer { alpha = trendingAlpha }) {
                                Text(
                                        "ПОПУЛЯРНЕ ЗАРАЗ",
                                    color = OnSurfaceVariant,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                
                                val rows = trending.take(12).chunked(GridDefaults.columns)
                                Column(verticalArrangement = Arrangement.spacedBy(GridDefaults.rowSpacing)) {
                                    rows.forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(GridDefaults.columnSpacing)
                                        ) {
                                            rowItems.forEach { movie ->
                                                MovieCard(
                                                    movie = movie,
                                                    width = CardDefaults.posterWidth,
                                                    height = CardDefaults.posterHeight,
                                                    onClick = {
                                                        viewModel.saveToHistory(movie.title)
                                                        onMovieClick(movie)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                                }
else if (history.isEmpty()) {
                                Text(
                                    "Почніть вводити назву для пошуку",
                                    color = OnSurfaceVariant
                                )
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
                        LazyVerticalGrid(
                            modifier = Modifier.graphicsLayer { alpha = resultsAlpha },
                            columns = GridCells.Fixed(GridDefaults.columns),
                            horizontalArrangement = Arrangement.spacedBy(GridDefaults.columnSpacing),
                            verticalArrangement = Arrangement.spacedBy(GridDefaults.rowSpacing),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(s.results, key = { it.id + it.pageUrl }) { movie ->
                                MovieCard(
                                    movie = movie,
                                    width = CardDefaults.posterWidth,
                                    height = CardDefaults.posterHeight,
                                    onClick = {
                                        viewModel.saveToHistory(query)
                                        onMovieClick(movie)
                                    }
                                )
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
