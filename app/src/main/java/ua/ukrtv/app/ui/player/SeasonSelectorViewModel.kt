package ua.ukrtv.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.ukrtv.app.data.streaming.StreamResolver
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject

@HiltViewModel
class SeasonSelectorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val streamResolver: StreamResolver
) : ViewModel() {

    private val _state = MutableStateFlow(SeasonsUiState())
    val state: StateFlow<SeasonsUiState> = _state.asStateFlow()

    val pageUrl: String = savedStateHandle.get<String>("url") ?: ""
    val contentId: String = savedStateHandle.get<String>("id") ?: ""
    private val voiceoverArg: String? = savedStateHandle.get<String>("voiceover")
    val initialSeason: Int? = savedStateHandle.get<Int>("season")?.takeIf { it != -1 }
    val initialEpisode: Int? = savedStateHandle.get<Int>("episode")?.takeIf { it != -1 }

    init {
        val title = savedStateHandle.get<String>("title") ?: ""
        _state.update { it.copy(
            title = title,
            currentSeason = initialSeason,
            currentEpisode = initialEpisode,
            currentVoiceover = voiceoverArg
        ) }
        loadSeasons()
    }

    private fun loadSeasons() {
        viewModelScope.launch {
            try {
                val res = streamResolver.resolve(pageUrl, isDeep = true)
                if (res?.seasons != null && res.seasons.isNotEmpty()) {
                    val firstSeason = initialSeason ?: res.seasons.first().number
                    val voiceover = voiceoverArg
                        ?: res.seasons.find { it.number == firstSeason }?.voiceoverOptions?.firstOrNull()
                    _state.update { it.copy(
                        isLoading = false,
                        seasons = res.seasons,
                        selectedSeason = firstSeason,
                        selectedVoiceover = if (res.seasons.any { s ->
                            s.voiceoverOptions.size > 1
                        }) voiceover else null
                    ) }
                } else {
                    _state.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                AppLogger.e("SeasonSelector", "Failed to load seasons", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectSeason(season: Int) {
        _state.update { state ->
            val seasonObj = state.seasons.find { it.number == season }
            val voiceover = state.selectedVoiceover
                ?: if (seasonObj?.voiceoverOptions?.size == 1) seasonObj.voiceoverOptions.first()
                else null
            state.copy(selectedSeason = season, selectedVoiceover = voiceover)
        }
    }

    fun selectVoiceover(voiceover: String) {
        _state.update { it.copy(selectedVoiceover = voiceover) }
    }
}
