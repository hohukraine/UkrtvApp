package ua.ukrtv.app.data.providers

import ua.ukrtv.app.util.AppLogger

object DleResolutionUtils {

    private val MEDIA_URL_REGEX = Regex("""https?://[^\s"'>]+(?:\.m3u8|\.mp4|\.webm)(?:\?[^\s"'>]*)?""", RegexOption.IGNORE_CASE)
    private val MEDIA_PLAYLIST_REGEX = Regex("""https?://[^\s"'>]+/(?:master\.m3u8|index\.m3u8|playlist\.m3u8)""", RegexOption.IGNORE_CASE)
    private val DLEID_REGEX = Regex("""dleid://(\d+)""")
    private val DATA_FILE_REGEX = Regex("""data-file=["'](//[^"']+)["']""", RegexOption.IGNORE_CASE)
    private val YEAR_CLEANUP_REGEX = Regex("""\b(19|20)\d{2}\b""")

    private val SEASON_REGEXES = listOf(
        Regex("""(?:сезон|season|sezon)[\s\-_]*([0-9]{1,2})""", RegexOption.IGNORE_CASE),
        Regex("""([0-9]{1,2})[\s\-_]*(?:сезон|season|sezon)""", RegexOption.IGNORE_CASE),
        Regex("""\bs(\d+)(?:e\d+)?\b""", RegexOption.IGNORE_CASE),
        Regex("""s(\d+)e\d+""", RegexOption.IGNORE_CASE),
        Regex("""/(\d+)[\s\-_]*сезон""", RegexOption.IGNORE_CASE)
    )

    fun findMediaUrlsInText(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val cleanText = if (text.contains("\\/")) text.replace("\\/", "/") else text
        if (!cleanText.contains(".m3u8") && !cleanText.contains(".mp4") &&
            !cleanText.contains(".webm") && !cleanText.contains("dleid://") &&
            !cleanText.contains("data-file")) return emptyList()

        val candidates = mutableSetOf<String>()

        MEDIA_URL_REGEX.findAll(cleanText).forEach { m -> candidates.add(m.value) }
        MEDIA_PLAYLIST_REGEX.findAll(cleanText).forEach { m -> candidates.add(m.value) }
        DLEID_REGEX.findAll(cleanText).forEach { m -> candidates.add(m.value) }
        DATA_FILE_REGEX.findAll(cleanText).forEach { m ->
            candidates.add("https:${m.groupValues[1]}")
        }

        return candidates.sorted()
    }

    fun extractSeasonNum(text: String): Int? {
        val clean = text.lowercase().replace(YEAR_CLEANUP_REGEX, "")
        return SEASON_REGEXES.firstNotNullOfOrNull { it.find(clean)?.groupValues?.get(1)?.toIntOrNull() }
    }

    fun ensureAbsoluteUrl(url: String, baseUrl: String): String {
        if (url.startsWith("http")) return url
        if (url.startsWith("//")) return "https:$url"
        if (url.all { it.isDigit() }) return "dleid://$url"
        if (url.startsWith("dleid://")) return url
        return try {
            val uri = java.net.URI(baseUrl)
            if (url.startsWith("/")) "${uri.scheme}://${uri.host}$url"
            else baseUrl.substringBeforeLast("/") + "/" + url
        } catch (e: Exception) {
            AppLogger.w("DleResolutionUtils", "Failed to resolve URL: ${e.message}")
            url
        }
    }

    private val QUALITY_4K_REGEX = Regex("""[/_\-](?:4k|2160p?|3840x2160)[/_\-.]""", RegexOption.IGNORE_CASE)
    private val QUALITY_1080_REGEX = Regex("""[/_\-](?:1080p?|1920x1080)[/_\-.]""", RegexOption.IGNORE_CASE)
    private val QUALITY_720_REGEX = Regex("""[/_\-](?:720p?|1280x720)[/_\-.]""", RegexOption.IGNORE_CASE)
    private val QUALITY_480_REGEX = Regex("""[/_\-](?:480p?|854x480)[/_\-.]""", RegexOption.IGNORE_CASE)
    private val QUALITY_360_REGEX = Regex("""[/_\-](?:360p?|640x360)[/_\-.]""", RegexOption.IGNORE_CASE)

    fun pickBestQuality(urls: List<String>, preferMaster: Boolean = true): String? {
        if (urls.isEmpty()) return null
        if (urls.size == 1) return urls.first()

        // 1. Prioritize Master Playlist if requested (let player decide)
        if (preferMaster) {
            val master = urls.firstOrNull { 
                it.contains("master.m3u8", ignoreCase = true) || 
                it.contains("playlist.m3u8", ignoreCase = true) ||
                it.contains("index.m3u8", ignoreCase = true)
            }
            if (master != null) return master
        }

        // 2. Score by resolution
        val qualityPatterns = listOf(
            QUALITY_4K_REGEX,
            QUALITY_1080_REGEX,
            QUALITY_720_REGEX,
            QUALITY_480_REGEX,
            QUALITY_360_REGEX,
        )

        val scored = urls.map { url ->
            val qualityIdx = qualityPatterns.indexOfFirst { it.containsMatchIn(url) }
            url to if (qualityIdx >= 0) qualityPatterns.size - qualityIdx else 0
        }

        val best = scored.maxByOrNull { it.second }
        if (best != null && best.second > 0) return best.first

        // 3. Fallback to first URL (likely original/default)
        return urls.first()
    }

    fun promoteToSeriesIfNeeded(source: MediaSource?, pageUrl: String, providerName: String): MediaSource? {
        if (source !is MediaSource.Movie || source.fallbackUrls.size <= 2) return source
        val allLinks = listOf(source.url) + source.fallbackUrls
        return SeriesPlaylistParser.parseUrlBasedSeries(allLinks, pageUrl, providerName) ?: source
    }

    fun resolveOtherSeasons(doc: org.jsoup.nodes.Document, pageUrl: String, logTag: String): List<Pair<Int, String>> {
        try {
            val currentId = pageUrl.substringAfterLast("/").substringBefore("-").toIntOrNull()
            val titleSlug = pageUrl.substringAfterLast("/").substringAfter("-").substringBefore("-sezon").takeIf { it.length > 3 }

            val source = doc.select(".seasons, .franchise-list, .serial-series, .related-ids, .video-tabs, .player-tabs, .tabs-sel")
            if (source.isNotEmpty()) {
                val links = source.select("a[href]").mapNotNull { a ->
                    val sNum = extractSeasonNum(a.text()) ?: return@mapNotNull null
                    if (sNum > 50) return@mapNotNull null
                    sNum to a.attr("abs:href")
                }
                if (links.isNotEmpty()) return links.distinctBy { it.second }.sortedBy { it.first }
            }

            return doc.select("a[href*='-sezon']").filter { a ->
                val href = a.attr("abs:href")
                val matchesId = currentId != null && href.contains("/$currentId-")
                val matchesSlug = titleSlug != null && href.contains(titleSlug)

                (matchesId || matchesSlug) &&
                a.parents().none { p ->
                    val cls = (p.className() + " " + p.id()).lowercase()
                    cls.contains("side") || cls.contains("sidebar") || cls.contains("related")
                }
            }.mapNotNull { a ->
                val sNum = extractSeasonNum(a.text()) ?: return@mapNotNull null
                if (sNum > 50) return@mapNotNull null
                sNum to a.attr("abs:href")
            }.distinctBy { it.second }.sortedBy { it.first }
        } catch (e: Exception) {
            AppLogger.w(logTag, "resolveOtherSeasons failed: ${e.message}")
            return emptyList()
        }
    }
}
