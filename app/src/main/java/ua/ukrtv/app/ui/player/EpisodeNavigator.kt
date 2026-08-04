package ua.ukrtv.app.ui.player

import ua.ukrtv.app.domain.model.Season

object EpisodeNavigator {

    data class NavigationResult(val season: Int, val episode: Int)

    fun nextEpisode(seasons: List<Season>?, currentSeason: Int?, currentEpisode: Int?): NavigationResult? {
        val allSeasons = seasons ?: return null
        val cSeason = currentSeason ?: return null
        val cEpisode = currentEpisode ?: return null

        val seasonIdx = allSeasons.indexOfFirst { it.number == cSeason }
        if (seasonIdx == -1) return null

        val season = allSeasons[seasonIdx]
        val episodes = season.episodes
        val epIdx = episodes.indexOfFirst { it.number == cEpisode }

        // Skip past the current episode, including any duplicate entries that share its
        // number (malformed providers can emit the same episode link multiple times).
        if (epIdx >= 0) {
            val nextEp = episodes.asSequence().drop(epIdx + 1).firstOrNull { it.number != cEpisode }
            if (nextEp != null) {
                return NavigationResult(cSeason, nextEp.number)
            }
        }

        if (seasonIdx < allSeasons.size - 1) {
            val nextSeason = allSeasons[seasonIdx + 1]
            if (nextSeason.episodes.isNotEmpty()) {
                return NavigationResult(nextSeason.number, nextSeason.episodes[0].number)
            }
        }

        return null
    }

    fun previousEpisode(seasons: List<Season>?, currentSeason: Int?, currentEpisode: Int?): NavigationResult? {
        val allSeasons = seasons ?: return null
        val cSeason = currentSeason ?: return null
        val cEpisode = currentEpisode ?: return null

        val seasonIdx = allSeasons.indexOfFirst { it.number == cSeason }
        if (seasonIdx == -1) return null

        val season = allSeasons[seasonIdx]
        val episodes = season.episodes
        val epIdx = episodes.indexOfLast { it.number == cEpisode }

        // Skip back past the current episode, including any duplicate entries that share
        // its number.
        if (epIdx >= 0) {
            val prevEp = episodes.asSequence().take(epIdx).lastOrNull { it.number != cEpisode }
            if (prevEp != null) {
                return NavigationResult(cSeason, prevEp.number)
            }
        }

        if (seasonIdx > 0) {
            val prevSeason = allSeasons[seasonIdx - 1]
            if (prevSeason.episodes.isNotEmpty()) {
                return NavigationResult(prevSeason.number, prevSeason.episodes.last().number)
            }
        }

        return null
    }

    fun hasNextEpisode(seasons: List<Season>?, currentSeason: Int?, currentEpisode: Int?): Boolean = 
        nextEpisode(seasons, currentSeason, currentEpisode) != null

    fun hasPreviousEpisode(seasons: List<Season>?, currentSeason: Int?, currentEpisode: Int?): Boolean = 
        previousEpisode(seasons, currentSeason, currentEpisode) != null
}
