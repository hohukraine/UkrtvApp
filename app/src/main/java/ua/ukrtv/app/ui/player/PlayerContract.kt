package ua.ukrtv.app.ui.player

import ua.ukrtv.app.domain.model.AppError
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.StreamType

data class PlayerState(
    val status: PlayerStatus = PlayerStatus.Idle,
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val showControls: Boolean = true,
    val showStats: Boolean = false,
    val childMode: Boolean = false,
    val videoResizeMode: Int = 1,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackState: Int = 1, // Player.STATE_IDLE
    val error: AppError? = null,
    val availableSeasons: List<Season>? = null,
    val currentSeason: Int? = null,
    val currentEpisode: Int? = null,
    val currentVoiceover: String? = null,
    val thermalStatus: Int = 0 // PowerManager.THERMAL_STATUS_NONE = 0
)

sealed class PlayerStatus {
    object Idle : PlayerStatus()
    data class Loading(val title: String) : PlayerStatus()
    data class Ready(
        val url: String,
        val title: String,
        val subtitle: String,
        val positionMs: Long,
        val referer: String,
        val streamType: StreamType,
        val loadTrigger: Long = System.currentTimeMillis()
    ) : PlayerStatus()
    data class Error(val message: String, val isRetryable: Boolean = true) : PlayerStatus()
}

sealed class PlayerIntent {
    data class Initialize(
        val contentId: String,
        val title: String,
        val pageUrl: String,
        val season: Int? = null,
        val episode: Int? = null,
        val poster: String = ""
    ) : PlayerIntent()
    
    object TogglePlay : PlayerIntent()
    object ToggleMute : PlayerIntent()
    object ToggleControls : PlayerIntent()
    object ToggleStats : PlayerIntent()
    object ToggleChildMode : PlayerIntent()
    object Retry : PlayerIntent()
    object NavigateNext : PlayerIntent()
    object NavigatePrevious : PlayerIntent()
    
    data class SeekTo(val positionMs: Long) : PlayerIntent()
    data class ChangeResizeMode(val mode: Int) : PlayerIntent()
    data class SetShowControls(val show: Boolean) : PlayerIntent()
    data class SelectEpisode(val season: Int, val episode: Int, val voiceover: String? = null) : PlayerIntent()
    data class UpdateProgress(val positionMs: Long, val durationMs: Long) : PlayerIntent()
    data class UpdatePlaybackState(val state: Int) : PlayerIntent()
    data class UpdateIsPlaying(val isPlaying: Boolean) : PlayerIntent()
}
