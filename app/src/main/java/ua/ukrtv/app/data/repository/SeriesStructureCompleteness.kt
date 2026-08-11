package ua.ukrtv.app.data.repository

/**
 * Decides whether a cached series structure (from Room) is complete enough to serve, given the
 * episode counts the precomputed index knows about. A cache with FEWER episodes than the index is
 * stale/partial (e.g. written before the slug was indexed) and must be re-resolved. A cache with
 * at least as many episodes is trusted, since the index can lag behind the live site.
 */
object SeriesStructureCompleteness {
    fun isCacheComplete(indexedEpisodeCount: Int?, cachedEpisodeCount: Int?): Boolean {
        if (indexedEpisodeCount == null) return true
        if (cachedEpisodeCount == null) return true
        return cachedEpisodeCount >= indexedEpisodeCount
    }
}
