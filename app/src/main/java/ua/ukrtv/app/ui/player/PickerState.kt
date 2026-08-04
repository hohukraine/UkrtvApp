package ua.ukrtv.app.ui.player

data class PickerColumn(
    val id: String,
    val label: String,
    val value: String,
    val enabled: Boolean = true,
    val needsCommit: Boolean = false
)
