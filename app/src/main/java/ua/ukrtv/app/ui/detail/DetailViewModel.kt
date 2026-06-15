package ua.ukrtv.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.usecase.GetMovieDetailsUseCase
import ua.ukrtv.app.domain.usecase.GetStreamUseCase
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.data.model.CachedMovie
import ua.ukrtv.app.data.repository.FavoritesRepository
import javax.inject.Inject

sealed class WatchState {
    object Idle : WatchState()
    object Searching : WatchState()
    object NotFound : WatchState()
}

data class PlayRequest(
    val streamResult: StreamResolutionResult,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val contentId: String,
    val seasons: List<Season>?
)

sealed class DetailState {
    object Loading : DetailState()
    data class Success(
        val detail: MovieDetail
    ) : DetailState() {
        val id: String get() = detail.id
        val title: String get() = detail.title
        val overview: String get() = detail.description
        val posterPath: String get() = detail.poster
        val backdropPath: String get() = detail.backdrop ?: detail.poster
        val releaseDate: String? get() = detail.year
        val type: ContentType get() = detail.contentType
    }
    data class Error(val message: String) : DetailState()
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getMovieDetailsUseCase: GetMovieDetailsUseCase,
    private val getStreamUseCase: GetStreamUseCase,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state

    private val _watchState = MutableStateFlow<WatchState>(WatchState.Idle)
    val watchState: StateFlow<WatchState> = _watchState

    private val _navigationEvent = MutableSharedFlow<PlayRequest>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<PlayRequest> = _navigationEvent.asSharedFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    init {
        val id = savedStateHandle.get<String>("id") ?: ""
        val url = savedStateHandle.get<String>("url") ?: ""
        if (id.isNotEmpty()) {
            loadDetail(id, url)
        } else {
            _state.value = DetailState.Error("Невірні дані")
        }
    }

    private fun loadDetail(id: String, url: String) {
        viewModelScope.launch {
            getMovieDetailsUseCase(id, url).collect { result ->
                result.onSuccess { detail ->
                    _state.value = DetailState.Success(detail)
                    _isFavorite.value = favoritesRepository.isFavorite(detail.id)
                }.onFailure { e ->
                    _state.value = DetailState.Error(e.message ?: "Помилка завантаження")
                }
            }
        }
    }

    fun toggleFavorite() {
        val currentState = _state.value as? DetailState.Success ?: return
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(
                CachedMovie(
                    id = currentState.detail.id,
                    title = currentState.detail.title,
                    posterPath = currentState.detail.poster,
                    backdropPath = currentState.detail.poster,
                    overview = currentState.detail.description,
                    year = currentState.detail.year ?: "",
                    rating = 0.0,
                    providerUrl = currentState.detail.pageUrl,
                    providerName = currentState.detail.providerName,
                    cachedAt = System.currentTimeMillis()
                )
            )
            _isFavorite.value = !_isFavorite.value
        }
    }

    fun watchContent(season: Int? = null, episode: Int? = null) {
        val currentState = _state.value as? DetailState.Success ?: return
        viewModelScope.launch {
            _watchState.value = WatchState.Searching
            
            // Якщо вибрано конкретну серію, пробуємо використати її URL
            val targetUrl = if (season != null && episode != null) {
                currentState.detail.seasons
                    ?.find { it.number == season }
                    ?.episodes?.find { it.number == episode }
                    ?.pageUrl ?: currentState.detail.pageUrl
            } else {
                currentState.detail.pageUrl
            }

            val streamResult = getStreamUseCase(targetUrl)
            if (streamResult != null) {
                _watchState.value = WatchState.Idle
                _navigationEvent.emit(
                    PlayRequest(
                        streamResult = streamResult,
                        title = currentState.title,
                        season = season,
                        episode = episode,
                        contentId = currentState.id,
                        seasons = currentState.detail.seasons
                    )
                )
            } else {
                _watchState.value = WatchState.NotFound
            }
        }
    }

    fun resetWatchState() {
        _watchState.value = WatchState.Idle
    }
}
