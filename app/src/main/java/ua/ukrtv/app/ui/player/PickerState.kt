package ua.ukrtv.app.ui.player

data class PickerColumn(
    val id: String,
    val label: String,
    val value: String,
    val enabled: Boolean = true,
    val needsCommit: Boolean = false
)

data class PickerState(
    val columns: List<PickerColumn> = emptyList(),
    val focusedIndex: Int = 0,
    val isActive: Boolean = false,
    val pendingSeason: Int? = null,
    val pendingEpisode: Int? = null,
    val pendingVoiceover: String? = null,
    val pendingTrackIndex: Int? = null
)
