package ua.ukrtv.app.ui.player

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import ua.ukrtv.app.domain.model.Season

data class SeasonsUiState(
    val isLoading: Boolean = true,
    val seasons: List<Season> = emptyList(),
    val selectedSeason: Int = 1,
    val selectedVoiceover: String? = null,
    val currentSeason: Int? = null,
    val currentEpisode: Int? = null,
    val currentVoiceover: String? = null,
    val title: String = ""
)

@Parcelize
data class SeasonSelectionResult(
    val season: Int,
    val episode: Int,
    val voiceover: String? = null
) : Parcelable
