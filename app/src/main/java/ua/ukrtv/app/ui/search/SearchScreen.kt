package ua.ukrtv.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.data.repository.ContentRepository
import ua.ukrtv.app.ui.home.MovieCard
import ua.ukrtv.app.ui.theme.Background
import ua.ukrtv.app.ui.theme.BrandBlue
import ua.ukrtv.app.ui.theme.CardDefaults
import ua.ukrtv.app.ui.theme.Error
import ua.ukrtv.app.ui.theme.GridDefaults
import ua.ukrtv.app.ui.theme.OnSurface
import ua.ukrtv.app.ui.theme.OnSurfaceVariant
import ua.ukrtv.app.ui.theme.OverlayLight
import ua.ukrtv.app.ui.theme.Surface
import ua.ukrtv.app.ui.theme.SurfaceFocus
import ua.ukrtv.app.ui.theme.SurfaceVariant
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (spokenText != null) {
                viewModel.search(spokenText)
                viewModel.saveToHistory(spokenText)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = GridDefaults.horizontalPadding, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = BrandBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isFocused) SurfaceFocus else OverlayLight,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { viewModel.updateQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            isFocused = state.isFocused
                            if (state.isFocused) {
                                keyboardController?.show()
                            }
                        },
                    textStyle = TextStyle(
                        color = OnSurface,
                        fontSize = 18.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(BrandBlue),
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
                        focusedContainerColor = BrandBlue
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

            Spacer(Modifier.width(8.dp))
            androidx.tv.material3.Surface(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажіть назву фільму або серіалу")
                    }
                    runCatching { voiceSearchLauncher.launch(intent) }
                },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = BrandBlue
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Голосовий пошук",
                    tint = OnSurface,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                is SearchState.Idle -> {
                    if (isFocused && suggestions.isNotEmpty()) {
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
                                        focusedContainerColor = BrandBlue.copy(alpha = 0.2f),
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
                                            tint = BrandBlue.copy(alpha = 0.6f),
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
                                Row(
                                    modifier = Modifier.padding(bottom = 32.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    history.forEach { item ->
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
                        color = BrandBlue
                    )
                }
                is SearchState.Success -> {
                    if (s.results.isEmpty()) {
                        Text(
                            "Нічого не знайдено",
                            modifier = Modifier.align(Alignment.Center),
                            color = OnSurfaceVariant
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(GridDefaults.columns),
                            horizontalArrangement = Arrangement.spacedBy(GridDefaults.columnSpacing),
                            verticalArrangement = Arrangement.spacedBy(GridDefaults.rowSpacing),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(s.results, key = { it.id + it.pageUrl }) { movie ->
                                var itemFocused by remember { mutableStateOf(false) }
                                MovieCard(
                                    movie = movie,
                                    width = CardDefaults.posterWidth,
                                    height = CardDefaults.posterHeight,
                                    isExpanded = itemFocused,
                                    modifier = Modifier.onFocusChanged { itemFocused = it.isFocused },
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
                    Text(
                        s.message,
                        modifier = Modifier.align(Alignment.Center),
                        color = Error
                    )
                }
            }
        }
    }
}
