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
        val epIdx = season.episodes.indexOfFirst { it.number == cEpisode }

        if (epIdx >= 0 && epIdx < season.episodes.size - 1) {
            return NavigationResult(cSeason, season.episodes[epIdx + 1].number)
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
        val epIdx = season.episodes.indexOfFirst { it.number == cEpisode }

        if (epIdx > 0) {
            return NavigationResult(cSeason, season.episodes[epIdx - 1].number)
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
