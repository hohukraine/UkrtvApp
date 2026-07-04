package ua.ukrtv.app.ui.player

import androidx.media3.common.C

data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val type: Int = C.TRACK_TYPE_VIDEO
)
