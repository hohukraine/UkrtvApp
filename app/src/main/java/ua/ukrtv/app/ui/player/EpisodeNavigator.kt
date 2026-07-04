package ua.ukrtv.app.ui.player

object EpisodeNavigator {

    data class NavigationResult(val season: Int, val episode: Int)

    fun nextEpisode(context: PlayerContext): NavigationResult? {
        val seasons = context.seasons ?: return null
        val currentSeason = context.season ?: return null
        val currentEpisode = context.episode ?: return null

        val seasonIdx = seasons.indexOfFirst { it.number == currentSeason }
        if (seasonIdx == -1) return null

        val season = seasons[seasonIdx]
        val epIdx = season.episodes.indexOfFirst { it.number == currentEpisode }

        if (epIdx >= 0 && epIdx < season.episodes.size - 1) {
            return NavigationResult(currentSeason, season.episodes[epIdx + 1].number)
        }

        if (seasonIdx < seasons.size - 1) {
            val nextSeason = seasons[seasonIdx + 1]
            if (nextSeason.episodes.isNotEmpty()) {
                return NavigationResult(nextSeason.number, nextSeason.episodes[0].number)
            }
        }

        return null
    }

    fun previousEpisode(context: PlayerContext): NavigationResult? {
        val seasons = context.seasons ?: return null
        val currentSeason = context.season ?: return null
        val currentEpisode = context.episode ?: return null

        val seasonIdx = seasons.indexOfFirst { it.number == currentSeason }
        if (seasonIdx == -1) return null

        val season = seasons[seasonIdx]
        val epIdx = season.episodes.indexOfFirst { it.number == currentEpisode }

        if (epIdx > 0) {
            return NavigationResult(currentSeason, season.episodes[epIdx - 1].number)
        }

        if (seasonIdx > 0) {
            val prevSeason = seasons[seasonIdx - 1]
            if (prevSeason.episodes.isNotEmpty()) {
                return NavigationResult(prevSeason.number, prevSeason.episodes.last().number)
            }
        }

        return null
    }

    fun hasNextEpisode(context: PlayerContext): Boolean = nextEpisode(context) != null

    fun hasPreviousEpisode(context: PlayerContext): Boolean = previousEpisode(context) != null
}
