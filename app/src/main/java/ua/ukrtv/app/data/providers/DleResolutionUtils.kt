package ua.ukrtv.app.data.providers

import ua.ukrtv.app.util.AppLogger

object DleResolutionUtils {

    private val MEDIA_URL_REGEX = Regex("""(?:https?:)?//[^\s"'>]+(?:\.m3u8|\.mp4|\.webm)(?:\?[^\s"'>]*)?""", RegexOption.IGNORE_CASE)
    private val MEDIA_PLAYLIST_REGEX = Regex("""(?:https?:)?//[^\s"'>]+/(?:master\.m3u8|index\.m3u8|playlist\.m3u8)""", RegexOption.IGNORE_CASE)
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

    // Episode markers. Deliberately avoids matching a bare "e" followed by digits, otherwise
    // content IDs like "34310-ferma-klarksona-5-sezon.html" get misread as episode numbers.
    private val EPISODE_REGEXES = listOf(
        Regex("""\bep(?:isode)?[\s._-]*([0-9]{1,3})""", RegexOption.IGNORE_CASE),
        Regex("""\bs[0-9]{1,2}e([0-9]{1,3})\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:season|sezon)-[0-9]{1,2}-episode-([0-9]{1,3})""", RegexOption.IGNORE_CASE),
        Regex("""(?:seriya|seria|серия|серія|эпизод|епізод)[\s._-]*([0-9]{1,3})""", RegexOption.IGNORE_CASE),
        Regex("""([0-9]{1,3})[\s._-]*(?:seriya|seria|серия|серія|эпизод|епізод)""", RegexOption.IGNORE_CASE)
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

    fun extractEpisodeNum(text: String): Int? {
        if (text.isEmpty()) return null
        return EPISODE_REGEXES.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1)?.toIntOrNull() }
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

    private val UAKINO_SEASON_URL_REGEX = Regex("""^(\d+)-(.+)-(\d+)-sezon\.html$""")

    /**
     * Returns other seasons as (seasonNumber, newsId). The news_id is read directly from each
     * season URL (format {newsId}-{slug}-{n}-sezon.html), so callers can POST playlists.php
     * without a per-season HTML round trip. Seasons are matched by slug, not by a shared
     * news_id — series like Druzy have a distinct news_id per season. Returns (season, url)
     * as a fallback for links whose URL does not parse.
     */
    fun resolveOtherSeasons(doc: org.jsoup.nodes.Document, pageUrl: String, logTag: String): List<Pair<Int, String>> {
        try {
            val pageMatch = UAKINO_SEASON_URL_REGEX.matchEntire(pageUrl.trimEnd('/').substringAfterLast('/'))
            val currentNewsId = pageMatch?.groupValues?.get(1)
            val titleSlug = pageMatch?.groupValues?.get(2)

            fun newsIdOrUrl(url: String): Pair<Int, String>? {
                val m = UAKINO_SEASON_URL_REGEX.matchEntire(url.trimEnd('/').substringAfterLast('/')) ?: return null
                if (titleSlug != null && m.groupValues[2] != titleSlug) return null
                val sNum = m.groupValues[3].toIntOrNull() ?: return null
                if (sNum > 50) return null
                return sNum to m.groupValues[1]
            }

            val source = doc.select(".seasons, .franchise-list, .serial-series, .related-ids, .video-tabs, .player-tabs, .tabs-sel")
            if (source.isNotEmpty()) {
                val links = source.select("a[href]").mapNotNull { a ->
                    val href = a.attr("abs:href")
                    newsIdOrUrl(href)?.let { return@mapNotNull it }
                    val sNum = extractSeasonNum(a.text()) ?: return@mapNotNull null
                    if (sNum > 50) return@mapNotNull null
                    sNum to href
                }
                if (links.isNotEmpty()) return links.distinctBy { it.second }.sortedBy { it.first }
            }

            return doc.select("a[href*='-sezon']").filter { a ->
                val href = a.attr("abs:href")
                val matchesId = currentNewsId != null && href.contains("/$currentNewsId-")
                val matchesSlug = titleSlug != null && href.contains(titleSlug)

                (matchesId || matchesSlug) &&
                a.parents().none { p ->
                    val cls = (p.className() + " " + p.id()).lowercase()
                    cls.contains("side") || cls.contains("sidebar") || cls.contains("related")
                }
            }.mapNotNull { a ->
                val href = a.attr("abs:href")
                newsIdOrUrl(href) ?: run {
                    val sNum = extractSeasonNum(a.text()) ?: return@mapNotNull null
                    if (sNum > 50) return@mapNotNull null
                    sNum to href
                }
            }.distinctBy { it.second }.sortedBy { it.first }
        } catch (e: Exception) {
            AppLogger.w(logTag, "resolveOtherSeasons failed: ${e.message}")
            return emptyList()
        }
    }
}
