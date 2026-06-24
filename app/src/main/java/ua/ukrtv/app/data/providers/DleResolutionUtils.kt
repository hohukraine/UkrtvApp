package ua.ukrtv.app.data.providers

import com.google.gson.Gson
import org.jsoup.Jsoup
import ua.ukrtv.app.util.AppLogger

object DleResolutionUtils {
    
    private val SEASON_REGEXES = listOf(
        Regex("""(?:сезон|season|sezon)[\s\-_]*([0-9]{1,2})""", RegexOption.IGNORE_CASE),
        Regex("""([0-9]{1,2})[\s\-_]*(?:сезон|season|sezon)""", RegexOption.IGNORE_CASE),
        Regex("""\bs(\d+)(?:e\d+)?\b""", RegexOption.IGNORE_CASE),
        Regex("""s(\d+)e\d+""", RegexOption.IGNORE_CASE),
        Regex("""/(\d+)[\s\-_]*сезон""", RegexOption.IGNORE_CASE)
    )

    fun findMediaUrlsInText(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        
        val candidates = mutableSetOf<String>()
        
        // Common media URL patterns (m3u8/mp4/webm)
        Regex(
            """https?://[^\s"'>]+(?:\.m3u8|\.mp4|\.webm)(?:\?[^\s"'>]*)?""",
            RegexOption.IGNORE_CASE
        ).findAll(text).forEach { m -> candidates.add(m.value) }
        
        // Playlist patterns without extension
        Regex(
            """https?://[^\s"'>]+/(?:master\.m3u8|index\.m3u8|playlist\.m3u8)""",
            RegexOption.IGNORE_CASE
        ).findAll(text).forEach { m -> candidates.add(m.value) }

        // dleid pattern
        Regex("""dleid://(\d+)""").findAll(text).forEach { m -> candidates.add(m.value) }
        
        return candidates.sorted()
    }

    fun extractSeasonNum(text: String): Int? {
        val clean = text.lowercase().replace(Regex("""\b(19|20)\d{2}\b"""), "")
        return SEASON_REGEXES.firstNotNullOfOrNull { it.find(clean)?.groupValues?.get(1)?.toIntOrNull() }
    }

    fun parseDleLinks(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains("<html", true)) return ""
        // Handle encoded formats like [720p]https://...
        if (trimmed.contains("]")) return trimmed.substringAfterLast("]").trim()
        return trimmed
    }

    fun parsePlaylist(input: String, referer: String, providerName: String, defaultSeason: Int? = null): MediaSource? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed == "0" || trimmed == "null" || trimmed.startsWith("<!")) return null

        // 1. Handle JSON
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val gson = Gson()
                val map = if (trimmed.startsWith("{")) gson.fromJson(trimmed, Map::class.java) else null
                
                val innerHtml = map?.get("response") as? String
                if (innerHtml != null && (innerHtml.contains("<li") || innerHtml.contains("data-file"))) {
                    return parsePlaylist(innerHtml, referer, providerName, defaultSeason)
                }

                val list = (map?.get("playlist") ?: map?.get("folder") ?: map?.get("video") ?: if (trimmed.startsWith("[")) gson.fromJson(trimmed, List::class.java) else null) as? List<*>
                
                if (list != null) {
                    val episodes = mutableListOf<ProviderEpisode>()
                    val seasonsList = mutableListOf<ProviderSeason>()

                    list.forEach { item ->
                        val m = item as? Map<*, *> ?: return@forEach
                        val folder = m["folder"] as? List<*>
                        
                        if (folder != null) {
                            val sTitle = (m["title"] as? String) ?: "Сезон"
                            val sNum = extractSeasonNum(sTitle) ?: (seasonsList.size + 1)
                            val sEpisodes = folder.mapIndexedNotNull { eIdx, eItem ->
                                val em = eItem as? Map<*, *> ?: return@mapIndexedNotNull null
                                val eTitle = (em["title"] as? String) ?: "Серія ${eIdx + 1}"
                                val eFile = parseDleLinks((em["file"] as? String) ?: (em["url"] as? String) ?: "")
                                if (eFile.isEmpty()) return@mapIndexedNotNull null
                                val eNum = Regex("""(\d+)""").find(eTitle)?.groupValues?.get(1)?.toIntOrNull() ?: (eIdx + 1)
                                
                                val eVoice = (em["voice"] as? String) ?: (em["voiceover"] as? String) ?: ""
                                val eSubs = (em["subtitle"] as? String) ?: (em["subtitles"] as? String) ?: (em["vtt"] as? String) ?: ""
                                
                                ProviderEpisode(
                                    eNum, 
                                    eTitle, 
                                    ensureAbsoluteUrl(eFile, referer), 
                                    eVoice.takeIf { it.isNotEmpty() },
                                    eSubs.takeIf { it.isNotEmpty() }
                                )
                            }
                            if (sEpisodes.isNotEmpty()) seasonsList.add(ProviderSeason(sNum, sEpisodes))
                        } else {
                            val title = (m["title"] as? String) ?: "Епізод"
                            val file = parseDleLinks((m["file"] as? String) ?: (m["url"] as? String) ?: "")
                            if (file.isNotEmpty()) {
                                val epNum = Regex("""(\d+)""").find(title)?.groupValues?.get(1)?.toIntOrNull() ?: (episodes.size + 1)
                                val voice = (m["voice"] as? String) ?: (m["voiceover"] as? String) ?: ""
                                val subs = (m["subtitle"] as? String) ?: (m["subtitles"] as? String) ?: (m["vtt"] as? String) ?: ""
                                episodes.add(
                                    ProviderEpisode(
                                        epNum, 
                                        title, 
                                        ensureAbsoluteUrl(file, referer),
                                        voice.takeIf { it.isNotEmpty() },
                                        subs.takeIf { it.isNotEmpty() }
                                    )
                                )
                            }
                        }
                    }
                    
                    if (seasonsList.isNotEmpty()) {
                        return MediaSource.Series(seasonsList.sortedBy { it.number }, referer, providerName)
                    } else if (episodes.isNotEmpty()) {
                        val sNum = defaultSeason ?: extractSeasonNum(referer) ?: 1
                        return MediaSource.Series(listOf(ProviderSeason(sNum, episodes)), referer, providerName)
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. HTML List
        if (trimmed.contains("<li") && (trimmed.contains("data-file") || trimmed.contains("data-url") || trimmed.contains("data-id"))) {
            val doc = Jsoup.parse(trimmed)
            val items = doc.select("li[data-file], li[data-url], li[data-id]")
            if (items.isEmpty()) return null
            
            val episodes = items.mapIndexedNotNull { idx, li ->
                val el = li!!
                val rawFile = el.attr("data-file").ifEmpty { el.attr("data-url") }.ifEmpty { el.attr("data-id") }
                val url = parseDleLinks(rawFile)
                if (url.isEmpty() || url == "0" || url.length < 5 || url.contains("<")) return@mapIndexedNotNull null
                
                val title = li.text().trim().ifEmpty { "Серія ${idx + 1}" }
                val epNum = Regex("""(\d+)""").find(title)?.groupValues?.get(1)?.toIntOrNull() ?: (idx + 1)
                
                val voice = li.attr("data-voice").ifEmpty {
                    li.attr("data-voiceover").ifEmpty {
                        val p = li.parent()
                        if (p != null && (p.hasClass("video-tabs") || p.hasClass("player-tabs") || p.hasClass("voice-tabs"))) li.text().trim() else ""
                    }
                }
                
                val subs = li.attr("data-subtitle").ifEmpty {
                    li.attr("data-subtitles").ifEmpty {
                        li.attr("data-vtt").ifEmpty { "" }
                    }
                }
                
                ProviderEpisode(
                    epNum, 
                    title, 
                    ensureAbsoluteUrl(url, referer), 
                    voice.takeIf { it.isNotEmpty() },
                    subs.takeIf { it.isNotEmpty() }
                )
            }
            
            if (episodes.isNotEmpty()) {
                val sNum = defaultSeason ?: extractSeasonNum(referer) ?: 1
                val seasons = episodes.groupBy { extractSeasonNum(it.title) ?: extractSeasonNum(it.url) ?: sNum }
                    .map { (num, eps) -> ProviderSeason(num, eps.sortedBy { it.number }) }.sortedBy { it.number }
                return MediaSource.Series(seasons, referer, providerName)
            }
        }

        // 3. Direct Link
        if (isMediaUrl(trimmed)) {
            val finalUrl = parseDleLinks(trimmed)
            if (finalUrl.isNotEmpty()) {
                return MediaSource.Movie(
                    url = ensureAbsoluteUrl(finalUrl, referer), 
                    fallbackUrls = emptyList(), 
                    referer = referer, 
                    providerName = providerName
                )
            }
        }

        return null
    }

    fun isMediaUrl(url: String): Boolean {
        if (url.length > 500 || url.contains("<") || url.contains(">") || url.contains("html", true)) return false
        val l = url.lowercase()
        if (l.startsWith("dleid://") || l.startsWith("[") || l.startsWith("{")) return true
        val markers = listOf(".m3u8", ".mp4", ".mpd", "/hls/", "/video/", "/vod/", "/embed/", "token=", "hdvb", "ashdi", "vidmoly", "mcloud")
        return markers.any { l.contains(it) }
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
        } catch (_: Exception) { url }
    }
}
