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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state

    private val _query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _query
                .debounce(500)
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
    val backgroundColor = remember { Color(0xFF0F0F0F) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(58.dp)
    ) {
        val titleColor = remember { Color.White }
        Text(
            "ПОШУК",
            color = titleColor,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        val textFieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFF1E3A8A),
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
        )
        val textFieldShape = remember { RoundedCornerShape(8.dp) }

        OutlinedTextField(
            value = query,
            onValueChange = { 
                query = it
                viewModel.search(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Введіть назву фільму або серіалу...", color = Color.Gray) },
            colors = textFieldColors,
            singleLine = true,
            shape = textFieldShape
        )

        Spacer(modifier = Modifier.height(48.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                is SearchState.Idle -> {
                    val idleTextColor = remember { Color.White.copy(alpha = 0.5f) }
                    Text(
                        "Почніть вводити назву для пошуку",
                        modifier = Modifier.align(Alignment.Center),
                        color = idleTextColor
                    )
                }
                is SearchState.Loading -> {
                    val loadingColor = remember { Color(0xFF1E3A8A) }
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = loadingColor
                    )
                }
                is SearchState.Success -> {
                    if (s.results.isEmpty()) {
                        val emptyTextColor = remember { Color.White.copy(alpha = 0.5f) }
                        Text(
                            "Нічого не знайдено",
                            modifier = Modifier.align(Alignment.Center),
                            color = emptyTextColor
                        )
                    } else {
                        val horizontalSpacing = remember { Arrangement.spacedBy(16.dp) }
                        val verticalSpacing = remember { Arrangement.spacedBy(24.dp) }
                        
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(180.dp),
                            horizontalArrangement = horizontalSpacing,
                            verticalArrangement = verticalSpacing
                        ) {
                            items(s.results, key = { it.id + it.pageUrl }) { movie ->
                                MovieCard(
                                    movie = movie,
                                    onClick = { onMovieClick(movie) }
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
