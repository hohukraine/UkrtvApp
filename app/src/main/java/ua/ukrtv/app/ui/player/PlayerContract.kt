package ua.ukrtv.app.ui.player

import ua.ukrtv.app.domain.model.AppError
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.player.AudioMode

data class CodecInfo(val mimeType: String, val displayName: String)

enum class ScaleMode(val label: String) {
    FIT("Оригінал"),
    ZOOM("Весь екран")
}

data class PlayerState(
    val status: PlayerStatus = PlayerStatus.Idle,
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val isShowingControls: Boolean = true,
    val scaleMode: ScaleMode = ScaleMode.FIT,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackState: Int = 1, // Player.STATE_IDLE
    val error: AppError? = null,
    val availableSeasons: List<Season>? = null,
    val currentSeason: Int? = null,
    val currentEpisode: Int? = null,
    val currentVoiceover: String? = null,
    val thermalStatus: Int = 0, // PowerManager.THERMAL_STATUS_NONE = 0
    val audioMode: AudioMode = AudioMode.NORMAL,
    val pickerColumns: List<PickerColumn> = emptyList(),
    val pickerFocusedIndex: Int = 0,
    val currentCodecDisplay: String = "",
    val availableCodecs: List<CodecInfo> = emptyList(),
    val shouldLaunchVlc: Boolean = false
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
