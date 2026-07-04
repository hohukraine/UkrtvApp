package ua.ukrtv.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TrackManager {

    private val _availableTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val availableTracks: StateFlow<List<TrackInfo>> = _availableTracks

    private val _selectedTrackIndex = MutableStateFlow<Int?>(null)
    val selectedTrackIndex: StateFlow<Int?> = _selectedTrackIndex

    private val _availableAudioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val availableAudioTracks: StateFlow<List<TrackInfo>> = _availableAudioTracks

    private val _selectedAudioTrackIndex = MutableStateFlow<Int?>(null)
    val selectedAudioTrackIndex: StateFlow<Int?> = _selectedAudioTrackIndex

    private val _availableSubtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val availableSubtitleTracks: StateFlow<List<TrackInfo>> = _availableSubtitleTracks

    private val _selectedSubtitleTrackIndex = MutableStateFlow<Int?>(null)
    val selectedSubtitleTrackIndex: StateFlow<Int?> = _selectedSubtitleTrackIndex

    fun updateAvailableTracks(tracks: Tracks) {
        val videoTracks = mutableListOf<TrackInfo>()
        val audioTracks = mutableListOf<TrackInfo>()
        val subtitleTracks = mutableListOf<TrackInfo>()

        for (groupIdx in tracks.groups.indices) {
            val group = tracks.groups[groupIdx]
            val dest = when (group.type) {
                C.TRACK_TYPE_VIDEO -> videoTracks
                C.TRACK_TYPE_AUDIO -> audioTracks
                C.TRACK_TYPE_TEXT -> subtitleTracks
                else -> continue
            }
            for (trackIdx in 0 until group.length) {
                val format = group.getTrackFormat(trackIdx)
                val label = buildString {
                    when (group.type) {
                        C.TRACK_TYPE_VIDEO -> {
                            if (format.height > 0) {
                                val standardHeights = setOf(240, 360, 480, 540, 576, 720, 1080, 1440, 2160, 4320)
                                val label = if (format.height in standardHeights) {
                                    "${format.height}p"
                                } else if (format.width > 0) {
                                    when {
                                        format.width >= 1920 -> "1080p"
                                        format.width >= 1280 -> "720p"
                                        format.width >= 854 -> "480p"
                                        format.width >= 640 -> "360p"
                                        else -> "${format.height}p"
                                    }
                                } else {
                                    "${format.height}p"
                                }
                                append(label)
                            } else if (format.bitrate > 0) append("${format.bitrate / 1000}kbps")
                            else append("Track $trackIdx")
                            if (format.frameRate > 0) append(" ${format.frameRate}fps")
                            if (format.codecs != null) append(" (${format.codecs})")
                        }
                        C.TRACK_TYPE_AUDIO -> {
                            val lang = format.language
                            if (!lang.isNullOrBlank()) append(lang.uppercase())
                            if (format.channelCount > 0) {
                                if (!lang.isNullOrBlank()) append(" ")
                                append("${format.channelCount}.0")
                            }
                            if (lang.isNullOrBlank() && format.channelCount <= 0) append("Track $trackIdx")
                            if (format.bitrate > 0) append(" ${format.bitrate / 1000}kbps")
                        }
                        C.TRACK_TYPE_TEXT -> {
                            val lang = format.language
                            if (!lang.isNullOrBlank()) append(lang.uppercase())
                            else append("Subtitle $trackIdx")
                            if (format.label != null) append(" (${format.label})")
                        }
                    }
                }
                dest.add(TrackInfo(groupIdx, trackIdx, label, group.type))
            }
        }

        _availableTracks.value = videoTracks
        _availableAudioTracks.value = audioTracks
        _selectedAudioTrackIndex.value = audioTracks.firstOrNull { trackIdxMatches(it, tracks) }?.trackIndex
        _availableSubtitleTracks.value = subtitleTracks
        _selectedSubtitleTrackIndex.value = subtitleTracks.firstOrNull { trackIdxMatches(it, tracks) }?.trackIndex
    }

    private fun trackIdxMatches(trackInfo: TrackInfo, tracks: Tracks): Boolean {
        val group = tracks.groups.getOrNull(trackInfo.groupIndex) ?: return false
        return group.isTrackSupported(trackInfo.trackIndex)
    }

    fun selectTrack(trackInfo: TrackInfo, player: ExoPlayer) {
        val tracks = player.currentTracks
        val group = tracks.groups.getOrNull(trackInfo.groupIndex) ?: return

        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackInfo.trackIndex))
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .addOverride(override)
            .build()
        when (trackInfo.type) {
            C.TRACK_TYPE_VIDEO -> _selectedTrackIndex.value = trackInfo.trackIndex
            C.TRACK_TYPE_AUDIO -> _selectedAudioTrackIndex.value = trackInfo.trackIndex
            C.TRACK_TYPE_TEXT -> _selectedSubtitleTrackIndex.value = trackInfo.trackIndex
        }
    }

    fun clearTrackOverride(player: ExoPlayer) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverrides()
            .build()
        _selectedTrackIndex.value = null
        _selectedAudioTrackIndex.value = null
        _selectedSubtitleTrackIndex.value = null
    }

    fun reset() {
        _availableTracks.value = emptyList()
        _selectedTrackIndex.value = null
        _availableAudioTracks.value = emptyList()
        _selectedAudioTrackIndex.value = null
        _availableSubtitleTracks.value = emptyList()
        _selectedSubtitleTrackIndex.value = null
    }
}
