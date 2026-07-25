package ua.ukrtv.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ua.ukrtv.app.data.providers.ProviderManager
import ua.ukrtv.app.data.repository.ContentRepository
import ua.ukrtv.app.domain.model.Movie
import javax.inject.Inject

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
