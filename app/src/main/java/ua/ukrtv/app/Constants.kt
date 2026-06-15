package ua.ukrtv.app

object Constants {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    const val CONNECT_TIMEOUT_MS = 10000L
    const val READ_TIMEOUT_MS = 10000L

    // HTML cache TTL: 5–10 minutes
    const val HTML_CACHE_TTL_MS = 7 * 60 * 1000L // 7 min

    // Cache TTLs (memory)
    const val SEARCH_CACHE_TTL_MS = 7 * 60 * 1000L // 5–10 min
    const val METADATA_CACHE_TTL_MS = 45 * 60 * 1000L // 30–60 min
    const val PLAYLIST_CACHE_TTL_MS = 7 * 60 * 1000L // 5–10 min

    // Stream resolution TTL: 1–3 min (tokens can expire)
    const val STREAM_RESOLUTION_CACHE_TTL_MS = 2 * 60 * 1000L // 2 min


    const val MAX_RETRIES = 3
    const val RETRY_DELAY_MS = 2000L
}
