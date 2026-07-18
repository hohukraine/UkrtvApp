package ua.ukrtv.app.data.providers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import ua.ukrtv.app.util.AppLogger

object SeriesPlaylistParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val HDVB_EP_REGEX = Regex("""s(\d+)e(\d+)""", RegexOption.IGNORE_CASE)
    private val DIGITS_REGEX = Regex("""(\d+)""")
    private val EP_TITLE_DIGITS = Regex("""(\d+)""")

    fun parseAjaxPlaylistHtml(html: String): Pair<List<ProviderEpisode>, List<String>> {
        val doc = Jsoup.parseBodyFragment(html)
        val voiceoverNames = doc.select(".playlists-lists .playlists-items li").mapNotNull { li ->
            li.text().trim().takeIf { it.isNotBlank() }
        }
        val voiceoverGroups = doc.select(".playlists-videos > .playlists-items")
        val cleanVoiceoverNames = if (voiceoverNames.isNotEmpty()) {
            voiceoverNames.map { it.substringBefore("(").trim() }
        } else {
            voiceoverGroups.indices.map { "Озвучка ${it + 1}" }
        }

        val hasDataVoice = voiceoverGroups.any { group ->
            group.select("li[data-file][data-voice]").isNotEmpty()
        }

        val episodesByNumber = mutableMapOf<Int, MutableMap<String, String>>()
        val usedVoiceoverNames = linkedSetOf<String>()

        for ((idx, group) in voiceoverGroups.withIndex()) {
            val vName = cleanVoiceoverNames.getOrNull(idx) ?: "Озвучка ${idx + 1}"
            for (li in group.select("li[data-file]")) {
                var file = li.attr("data-file").ifEmpty { li.attr("data-src") }.ifEmpty { li.attr("data-url") }
                if (file.isBlank()) continue
                if (file.startsWith("//")) file = "https:$file"

                val episodeVoice = if (hasDataVoice) {
                    li.attr("data-voice").ifBlank { vName }.trim()
                } else {
                    vName
                }
                usedVoiceoverNames.add(episodeVoice)

                val epNumMatch = DIGITS_REGEX.find(li.text())
                val epNum = epNumMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1 // Default to 1 if no number found (movie mode)
                
                if (episodesByNumber.containsKey(epNum) && episodesByNumber[epNum]?.containsKey(episodeVoice) == true && epNumMatch == null) {
                    // If we already have episode 1 for this voice and current li also has no number, 
                    // it might be a truly multi-episode list without numbers (unlikely) or just duplicate data.
                    // We'll increment to avoid overwriting if it's clearly a list.
                    var nextEp = epNum + 1
                    while (episodesByNumber.containsKey(nextEp) && episodesByNumber[nextEp]?.containsKey(episodeVoice) == true) {
                        nextEp++
                    }
                    episodesByNumber.getOrPut(nextEp) { mutableMapOf() }[episodeVoice] = file
                } else {
                    episodesByNumber.getOrPut(epNum) { mutableMapOf() }[episodeVoice] = file
                }
            }
        }

        val finalVoiceoverNames = if (hasDataVoice) usedVoiceoverNames.toList() else cleanVoiceoverNames

        val episodes = episodesByNumber.flatMap { (epNum, urls) ->
            urls.map { (vName, file) ->
                ProviderEpisode(epNum, "Серія $epNum", file,
                    voiceover = vName)
            }
        }.sortedBy { it.number }

        return episodes to finalVoiceoverNames
    }

    fun parseUrlBasedSeries(media: List<String>, pageUrl: String, providerName: String): MediaSource.Series? {
        data class TempEp(val s: Int, val e: Int, val url: String)
        val tempEpisodes = media.mapNotNull { url ->
            val hdvbMatch = HDVB_EP_REGEX.find(url)
            if (hdvbMatch != null) {
                val s = hdvbMatch.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val e = hdvbMatch.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                return@mapNotNull TempEp(s, e, url)
            }
            val parts = url.split("/")
            val indexIdx = parts.indexOfLast { it.contains("index.m3u8") }
            if (indexIdx >= 2) {
                val eNum = parts[indexIdx - 1].toIntOrNull()
                val sNum = parts[indexIdx - 2].toIntOrNull()
                if (eNum != null && sNum != null) {
                    if (sNum > 100 && indexIdx >= 3) {
                        val s = parts[indexIdx - 3].toIntOrNull()
                        val e = parts[indexIdx - 2].toIntOrNull()
                        if (s != null && e != null && s < 100) return@mapNotNull TempEp(s, e, url)
                    }
                    if (sNum < 100) return@mapNotNull TempEp(sNum, eNum, url)
                }
            }
            null
        }

        if (tempEpisodes.isNotEmpty()) {
            val groupedSeasons = tempEpisodes.groupBy { it.s }
                .map { (sNum, eps) ->
                    val episodes = eps.distinctBy { it.e }
                        .map { ProviderEpisode(it.e, "Серія ${it.e}", it.url) }
                        .sortedBy { it.number }
                    ProviderSeason(sNum, episodes)
                }.sortedBy { it.number }
            return MediaSource.Series(groupedSeasons, pageUrl, providerName)
        }

        if (media.size > 2) {
            val episodes = media.mapIndexed { idx, url ->
                ProviderEpisode(idx + 1, "Серія ${idx + 1}", url)
            }
            return MediaSource.Series(listOf(ProviderSeason(1, episodes)), pageUrl, providerName)
        }
        return null
    }

    fun extractBalancedJson(text: String): String? {
        val start = text.indexOf('[')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escape -> escape = false
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                !inString -> {
                    when (c) {
                        '[' -> depth++
                        ']' -> {
                            depth--
                            if (depth == 0) return text.substring(start, i + 1)
                        }
                    }
                }
            }
        }
        return null
    }

    fun parseJsonPlaylist(rawJson: String, pageUrl: String, providerName: String): MediaSource.Series? {
        val array = try { json.decodeFromString<JsonArray>(rawJson) } catch (e: Exception) {
            AppLogger.w("$providerName:JsonPlaylist", "Parse failed: ${e.message}")
            return null
        }
        val items = array.mapNotNull { it as? JsonObject }
        if (items.isEmpty()) return null

        val hasSeasonTitles = items.any { item ->
            val title = item["title"]?.jsonPrimitive?.content ?: ""
            DleResolutionUtils.extractSeasonNum(title) != null ||
                    title.contains("сезон", true) || title.contains("season", true)
        }

        if (hasSeasonTitles) {
            return parseSeasonsFromJson(items, pageUrl, providerName)
        }

        val hasNestedSeasonFolders = items.any { item ->
            val folder = item["folder"]?.jsonArray
            folder?.any { f ->
                val fObj = f as? JsonObject
                val title = fObj?.get("title")?.jsonPrimitive?.content ?: ""
                fObj?.get("folder")?.jsonArray != null &&
                    (DleResolutionUtils.extractSeasonNum(title) != null || title.contains("сезон", true))
            } == true
        }

        if (hasNestedSeasonFolders) {
            return parseVoiceoverSeasonJson(items, pageUrl, providerName)
        }

        return parseDirectVoiceoverJson(items, pageUrl, providerName)
    }

    fun parseSeasonsFromJson(items: List<JsonObject>, pageUrl: String, providerName: String): MediaSource.Series? {
        val seasons = mutableListOf<ProviderSeason>()
        for (item in items) {
            val sNum = DleResolutionUtils.extractSeasonNum(item["title"]?.jsonPrimitive?.content ?: "") ?: (seasons.size + 1)
            val voiceFolders = item["folder"]?.jsonArray ?: continue
            val voiceoverNames = voiceFolders.mapNotNull { f ->
                (f as? JsonObject)?.get("title")?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
            }
            val cleanVoiceNames = voiceoverNames.map { it.substringBefore("(").trim() }
            val episodesByNum = mutableMapOf<Int, MutableMap<String, String>>()

            for ((vIdx, voiceItem) in voiceFolders.withIndex()) {
                val vm = voiceItem as? JsonObject ?: continue
                val eps = vm["folder"]?.jsonArray ?: continue
                val vName = cleanVoiceNames.getOrNull(vIdx) ?: "Озвучка ${vIdx + 1}"
                for (ep in eps) {
                    val em = ep as? JsonObject ?: continue
                    val url = em["file"]?.jsonPrimitive?.content ?: continue
                    val title = em["title"]?.jsonPrimitive?.content ?: ""
                    val epNum = EP_TITLE_DIGITS.find(title)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                    episodesByNum.getOrPut(epNum) { mutableMapOf() }[vName] = url
                }
            }

            val allEpisodes = episodesByNum.flatMap { (epNum, urls) ->
                urls.map { (vName, file) ->
                    ProviderEpisode(epNum, "Серія $epNum", file,
                        voiceover = vName)
                }
            }
            if (allEpisodes.isNotEmpty()) {
                seasons.add(ProviderSeason(sNum, allEpisodes.sortedBy { it.number },
                    voiceoverOptions = cleanVoiceNames))
            }
        }
        return if (seasons.isNotEmpty()) {
            MediaSource.Series(seasons.sortedBy { it.number }, pageUrl, providerName)
        } else null
    }

    fun parseDirectVoiceoverJson(items: List<JsonObject>, pageUrl: String, providerName: String): MediaSource.Series? {
        val cleanVoiceNames = items.mapNotNull { item ->
            item["title"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
                ?.let { it.substringBefore("(").trim() }
        }
        val epsByNum = mutableMapOf<Int, MutableMap<String, String>>()
        for ((vIdx, item) in items.withIndex()) {
            val vName = cleanVoiceNames.getOrNull(vIdx) ?: "Озвучка ${vIdx + 1}"
            val folder = item["folder"]?.jsonArray ?: continue
            for (ep in folder) {
                val em = ep as? JsonObject ?: continue
                val url = em["file"]?.jsonPrimitive?.content ?: continue
                val epNum = EP_TITLE_DIGITS.find(em["title"]?.jsonPrimitive?.content ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: continue
                epsByNum.getOrPut(epNum) { mutableMapOf() }[vName] = url
            }
        }
        if (epsByNum.isEmpty()) return null
        val allEps = epsByNum.flatMap { (epNum, urls) ->
            urls.map { (vName, file) ->
                ProviderEpisode(epNum, "Серія $epNum", file,
                    voiceover = vName)
            }
        }
        return MediaSource.Series(
            listOf(ProviderSeason(1, allEps.sortedBy { it.number },
                voiceoverOptions = cleanVoiceNames)),
            pageUrl, providerName
        )
    }

    fun parseVoiceoverSeasonJson(items: List<JsonObject>, pageUrl: String, providerName: String): MediaSource.Series? {
        val seasonMap = mutableMapOf<String, MutableMap<String, JsonArray>>()
        for (voiceoverItem in items) {
            val vName = voiceoverItem["title"]?.jsonPrimitive?.content?.trim()
                ?.substringBefore("(")?.trim() ?: continue
            val seasons = voiceoverItem["folder"]?.jsonArray ?: continue
            for (season in seasons) {
                val sObj = season as? JsonObject ?: continue
                val sTitle = sObj["title"]?.jsonPrimitive?.content?.trim() ?: continue
                val eps = sObj["folder"]?.jsonArray ?: continue
                seasonMap.getOrPut(sTitle) { mutableMapOf() }[vName] = eps
            }
        }
        if (seasonMap.isEmpty()) return null

        val cleanVoiceNames = items.mapNotNull { item ->
            item["title"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
                ?.let { it.substringBefore("(").trim() }
        }

        val seasons = seasonMap.mapNotNull { (sTitle, voiceoverEps) ->
            val sNum = DleResolutionUtils.extractSeasonNum(sTitle) ?: return@mapNotNull null
            val episodesByNum = mutableMapOf<Int, MutableMap<String, String>>()
            for ((vName, eps) in voiceoverEps) {
                for (ep in eps) {
                    val em = ep as? JsonObject ?: continue
                    val url = em["file"]?.jsonPrimitive?.content ?: continue
                    val epTitle = em["title"]?.jsonPrimitive?.content ?: ""
                    val epNum = EP_TITLE_DIGITS.find(epTitle)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                    episodesByNum.getOrPut(epNum) { mutableMapOf() }[vName] = url
                }
            }
            val allEpisodes = episodesByNum.flatMap { (epNum, urls) ->
                urls.map { (vName, file) ->
                    ProviderEpisode(epNum, "Серія $epNum", file,
                        voiceover = vName)
                }
            }.sortedBy { it.number }
            if (allEpisodes.isEmpty()) return@mapNotNull null
            ProviderSeason(sNum, allEpisodes, voiceoverOptions = cleanVoiceNames)
        }.sortedBy { it.number }

        return if (seasons.isNotEmpty()) {
            MediaSource.Series(seasons, pageUrl, providerName)
        } else null
    }
}
