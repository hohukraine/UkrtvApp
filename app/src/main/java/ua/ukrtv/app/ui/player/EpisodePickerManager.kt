package ua.ukrtv.app.ui.player

import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.player.AudioEngine

class EpisodePickerManager(
    private val audioEngine: AudioEngine
) {
    var pendingSeason: Int? = null
    var pendingEpisode: Int? = null
    var pendingVoiceover: String? = null
    var pendingTrackIndex: Int? = null

    fun rebuildPickerColumns(
        availableSeasons: List<Season>?,
        currentSeason: Int?,
        currentEpisode: Int?,
        currentVoiceover: String?,
        currentCodecDisplay: String,
        trackManager: TrackManager,
        engineVideoTracks: Array<PlaybackTrackInfo> = emptyArray()
    ): List<PickerColumn> {
        val cols = mutableListOf<PickerColumn>()

        if (availableSeasons != null && availableSeasons.isNotEmpty()) {
            val allEpisodesAreOne = availableSeasons.all { it.episodes.all { ep -> ep.number <= 1 } }

            if (pendingSeason == null) pendingSeason = currentSeason ?: availableSeasons.first().number
            val sNum = pendingSeason!!
            val currentSeasonData = availableSeasons.find { it.number == sNum } ?: availableSeasons.first()
            val eps = currentSeasonData.episodes.sortedBy { it.number }

            if (pendingEpisode == null) pendingEpisode = currentEpisode ?: eps.firstOrNull()?.number ?: 1
            val eNum = pendingEpisode!!

            val voOptions = currentSeasonData.voiceoverOptions.filter { it.isNotBlank() }
            if (pendingVoiceover == null) {
                pendingVoiceover = currentVoiceover.takeIf { it != null && voOptions.contains(it) } ?: voOptions.firstOrNull()
            }

            if (!allEpisodesAreOne) {
                cols.add(PickerColumn("season", "СЕЗОН", sNum.toString(), true))
                cols.add(PickerColumn("episode", "СЕРІЯ", eNum.toString(), true))
            }

            if (voOptions.size > 1) {
                cols.add(PickerColumn("voiceover", "ОЗВУЧКА", pendingVoiceover ?: voOptions.first(), true))
            }
        }

        cols.add(PickerColumn("audio_mode", "АУДІО", audioEngine.getMode().label))

        val tracks = trackManager.availableTracks.value
        if (tracks.isNotEmpty()) {
            val selectedIdx = pendingTrackIndex ?: trackManager.selectedTrackIndex.value
            val value = if (selectedIdx == null) "Auto" else tracks.getOrNull(selectedIdx)?.label?.substringBefore(" (") ?: "Auto"
            cols.add(PickerColumn("video_track", "ЯКІСТЬ", value))
        } else if (engineVideoTracks.size > 1) {
            val trackIdx = pendingTrackIndex ?: 0
            cols.add(PickerColumn("video_track", "ЯКІСТЬ", engineVideoTracks.getOrNull(trackIdx)?.name?.substringBefore(" (") ?: "—"))
        }

        if (currentCodecDisplay.isNotEmpty()) {
            cols.add(PickerColumn("codec", "КОДЕК", currentCodecDisplay))
        }

        return cols
    }
}

data class PlaybackTrackInfo(val id: Int, val name: String)
