package ua.ukrtv.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.MediaLaunchState
import ua.ukrtv.app.data.repository.ContentRepository
import ua.ukrtv.app.data.repository.WatchProgressRepository
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject

sealed class DetailState {
    object Loading : DetailState()
    data class Success(
        val detail: MovieDetail,
        val watchProgress: Long = 0L
    ) : DetailState()
    data class Error(val message: String) : DetailState()
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: ContentRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val streamResolver: StreamResolver
) : ViewModel() {

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state

    private val _launchState = MutableStateFlow<MediaLaunchState>(MediaLaunchState.Idle)
    val launchState: StateFlow<MediaLaunchState> = _launchState

    private var currentDetail: MovieDetail? = null

    init {
        val id = savedStateHandle.get<String>("id")
        val url = savedStateHandle.get<String>("url")
        val typeStr = savedStateHandle.get<String>("type")
        val type = typeStr?.let { try { ContentType.valueOf(it) } catch (_: Exception) { null } }

        if (id != null && url != null) {
            loadDetail(id, url, type)
        } else {
            _state.value = DetailState.Error("Відсутні дані для завантаження")
        }
    }

    private fun loadDetail(id: String, url: String, type: ContentType?) {
        viewModelScope.launch {
            _state.value = DetailState.Loading
            
            // Get progress first
            val progress = watchProgressRepository.getProgress(id)

            mediaRepository.getDetails(id, url, type?.let { 
                if (it == ContentType.SERIES) ua.ukrtv.app.data.providers.ContentCategory.SERIES 
                else ua.ukrtv.app.data.providers.ContentCategory.MOVIES 
            }).collect { result ->
                result.onSuccess { detail ->
                    currentDetail = detail
                    _state.value = DetailState.Success(
                        detail = detail,
                        watchProgress = progress?.positionMs ?: 0L
                    )
                }.onFailure { e ->
                    _state.value = DetailState.Error(e.message ?: "Не вдалося завантажити деталі")
                }
            }
        }
    }

    fun watchContent(season: Int? = null, episode: Int? = null) {
        val detail = currentDetail ?: return
        
        viewModelScope.launch {
            _launchState.value = MediaLaunchState.Resolving(detail.title)
            
            val res = withContext(Dispatchers.IO) {
                streamResolver.resolve(
                    url = detail.pageUrl,
                    referer = "",
                    season = season,
                    episode = episode
                )
            }

            if (res != null) {
                _launchState.value = MediaLaunchState.Ready(
                    contentId = detail.id,
                    title = detail.title,
                    subtitle = if (season != null && episode != null) "S$season E$episode" else "",
                    posterUrl = detail.poster,
                    streamResult = res,
                    season = season,
                    episode = episode,
                    seasons = res.seasons
                )
            } else {
                _launchState.value = MediaLaunchState.Error("Стрім не знайдено")
                AppLogger.e("DetailViewModel", "Failed to resolve stream for ${detail.title}")
            }
        }
    }

    fun resetLaunchState() {
        _launchState.value = MediaLaunchState.Idle
    }
}
