package ua.ukrtv.app.data.parsing

import ua.ukrtv.app.data.providers.MediaSource

/**
 * Common contract for all providers to parse seasons/episodes from provider HTML.
 *
 * Parser must produce a canonical MediaSource.Series where:
 * - season.number is the human-readable season number (1..N)
 * - episode.number is the episode number (1..M)
 */
interface SeasonParser {
    suspend fun parseSeries(pageUrl: String, html: String, referer: String = ""): MediaSource?
}

