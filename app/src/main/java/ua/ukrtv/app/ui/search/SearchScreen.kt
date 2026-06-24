package ua.ukrtv.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.data.repository.ContentRepository
import ua.ukrtv.app.ui.home.MovieCard
import javax.inject.Inject

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<Movie>) : SearchState()
    data class Error(val message: String) : SearchState()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val historyDao: ua.ukrtv.app.data.local.dao.SearchHistoryDao
) : ViewModel() {
    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state

    private val _query = MutableStateFlow("")

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history

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
            _history.value = historyDao.getRecent().map { it.query }
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
}

@Composable
fun SearchScreen(
    onMovieClick: (Movie) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(32.dp)
    ) {
        Text(
            "ПОШУК",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { 
                query = it
                viewModel.search(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text("Введіть назву фільму або серіалу...", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF1E3A8A),
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
            ),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                is SearchState.Idle -> {
                    val trending by viewModel.trendingMovies.collectAsState()
                    val history by viewModel.history.collectAsState()
                    
                    Column {
                        if (history.isNotEmpty()) {
                            Text(
                                "ОСТАННІ ЗАПИТИ",
                                color = Color.White.copy(alpha = 0.5f),
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
                                    androidx.tv.material3.Surface(
                                        onClick = { 
                                            query = item
                                            viewModel.search(item)
                                        },
                                        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                                        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                                            containerColor = Color.White.copy(alpha = 0.1f),
                                            contentColor = Color.White
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
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(trending, key = { "trending_" + it.id }) { movie ->
                                    MovieCard(
                                        movie = movie,
                                        onClick = { 
                                            viewModel.saveToHistory(query)
                                            onMovieClick(movie) 
                                        }
                                    )
                                }
                            }
                        } else if (history.isEmpty()) {
                            Text(
                                "Почніть вводити назву для пошуку",
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                is SearchState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF1E3A8A)
                    )
                }
                is SearchState.Success -> {
                    if (s.results.isEmpty()) {
                        Text(
                            "Нічого не знайдено",
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(s.results, key = { it.id + it.pageUrl }) { movie ->
                                MovieCard(
                                    movie = movie,
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
                        color = Color.Red
                    )
                }
            }
        }
    }
}
