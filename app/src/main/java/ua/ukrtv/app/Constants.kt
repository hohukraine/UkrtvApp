package ua.ukrtv.app

object Constants {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 11; BRAVIA 4K VH2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36"

    const val CONNECT_TIMEOUT_MS = 15000L
    const val READ_TIMEOUT_MS = 20000L
    const val MAX_RETRIES = 3
    const val RETRY_DELAY_MS = 3000L
    const val STREAM_RESOLUTION_TIMEOUT_MS = 25000L
    const val STREAM_ENRICH_TIMEOUT_MS = 45000L
    const val PER_SEASON_FETCH_TIMEOUT_MS = 5000L
    const val SERIES_FETCH_STAGGER_MS = 200L

    const val SERIES_STRUCTURE_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L
    const val SERIES_STRUCTURE_CACHE_STALE_TTL_MS = 14 * 24 * 60 * 60 * 1000L
    const val SERIES_STRUCTURE_CACHE_CLEANUP_MS = 30L * 24 * 60 * 60 * 1000

    const val HTML_CACHE_TTL_MS = 7 * 60 * 1000L
    const val HTML_CACHE_STALE_TTL_MS = 30 * 60 * 1000L  // 30 хвилин (stale-while-revalidate)
    const val SEARCH_CACHE_TTL_MS = 7 * 60 * 1000L
    const val METADATA_CACHE_TTL_MS = 45 * 60 * 1000L
    const val STREAM_RESOLUTION_CACHE_TTL_MS = 5 * 60 * 1000L
    const val STREAM_DB_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    const val DB_CLEANUP_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000
}
