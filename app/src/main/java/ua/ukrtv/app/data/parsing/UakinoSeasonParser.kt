package ua.ukrtv.app.data.parsing

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.providers.MediaSource
import ua.ukrtv.app.data.providers.ProviderEpisode
import ua.ukrtv.app.data.providers.ProviderSeason
import ua.ukrtv.app.data.streaming.HlsExtractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Season-only parser for Uakino.
 *
 * IMPORTANT: This class must NOT be a Provider (no StreamProvider/Search contracts).
 * It only returns MediaSource.Series(seasons=...) with human-readable season.number.
 */
@Singleton
class UakinoSeasonParser @Inject constructor(
    private val uakinoApi: ua.ukrtv.app.data.api.UakinoApi
) : SeasonParser {

    private val tag = "UakinoSeasonParser"
    private val baseUrl = "https://uakino.best/"
    private val providerName = "Uakino"
    private val semaphore = Semaphore(3)

    override suspend fun parseSeries(pageUrl: String, html: String, referer: String): MediaSource? =
        withContext(Dispatchers.IO) {
            Log.d(tag, "Parsing series for $pageUrl")
            val doc = Jsoup.parse(html)

            // IMPORTANT: this parser is used to fill Detail.seasons.
            // We must only parse as SERIES when the URL clearly points to serial-like pages,
            // otherwise non-series pages (films) will incorrectly receive seasons.
            val looksLikeSeries = pageUrl.contains("/seriesss/") ||
                pageUrl.contains("/seriali/") ||
                pageUrl.contains("/anime/") ||
                pageUrl.contains("/animeukr/") ||
                pageUrl.contains("/cartoons/") ||
                pageUrl.contains("/cartoon/")
            if (!looksLikeSeries) return@withContext null
            
            // Additional safety: if the HTML doesn't contain any playlists/season tabs,
            // don't fabricate seasons for films.
            val hasSeasonUi = doc.select(".playlists-ajax, .playlists-lists li, .playlists-videos li").isNotEmpty()
            if (!hasSeasonUi) return@withContext null

            // 1) AJAX playlists (best)
            val ajaxSeasons = parseAjaxSeasons(doc, html, referer.ifBlank { pageUrl })
            if (!ajaxSeasons.isNullOrEmpty()) {
                Log.d(tag, "Found AJAX playlist: ${ajaxSeasons.size} seasons")
                return@withContext MediaSource.Series(ajaxSeasons, pageUrl, providerName)
            }

            // 2) Playerjs config from scripts (fallback)
            val playerSeasons = parsePlayerJsonSeasons(doc)
            if (!playerSeasons.isNullOrEmpty()) {
                Log.d(tag, "Found Playerjs seasons: ${playerSeasons.size} seasons")
                return@withContext MediaSource.Series(playerSeasons, pageUrl, providerName)
            }
            
            // 3) Static links detection (last resort for non-standard DLE templates)
            val staticSeries = parseStaticLinksToSeasons(doc, pageUrl)
            if (staticSeries != null) {
                Log.d(tag, "Found static link series: ${staticSeries.seasons.size} seasons")
                return@withContext staticSeries
            }

            Log.w(tag, "No series data found for $pageUrl")
            null
        }

    private suspend fun parseStaticLinksToSeasons(doc: org.jsoup.nodes.Document, pageUrl: String): MediaSource.Series? {
        val seriesContainer = doc.selectFirst(".playlists-videos, .playlists-videos-list, .movie-parts, .video-list") ?: return null
        val items = seriesContainer.select("li, a[data-file], a[data-src]")
        if (items.isEmpty()) return null
        
        val episodes = mutableListOf<ProviderEpisode>()
        items.forEachIndexed { i, item ->
            val file = item.attr("data-file").ifEmpty { item.attr("data-src") }.ifEmpty { item.attr("href") }
            if (file.contains(".m3u8") || file.contains("/vod/")) {
                val title = item.text().trim().ifEmpty { "Серія ${i + 1}" }
                episodes.add(ProviderEpisode(i + 1, title, if (file.startsWith("//")) "https:$file" else file))
            }
        }
        
        return if (episodes.isNotEmpty()) {
            MediaSource.Series(listOf(ProviderSeason(1, episodes)), pageUrl, providerName)
        } else null
    }

    private suspend fun parseAjaxSeasons(
        doc: org.jsoup.nodes.Document,
        rawHtml: String,
        referer: String
    ): List<ProviderSeason>? {
        val playlistDiv = doc.selectFirst(".playlists-ajax") ?: doc.selectFirst("[data-news_id]") ?: return null
        val newsId = playlistDiv.attr("data-news_id")
        if (newsId.isBlank()) return null

        // 1. Try to get xfname directly from HTML
        var detectedXfname = playlistDiv.attr("data-xfname")
        if (detectedXfname.isBlank()) {
            detectedXfname = playlistDiv.attr("data-xfield")
        }
        
        // 2. If not in attributes, try to find in scripts or guess
        val possibleXfields = mutableListOf<String>()
        if (detectedXfname.isNotBlank()) {
            possibleXfields.add(detectedXfname)
        }
        
        // Fallback candidates
        val isSeries = referer.contains("/seriesss/") || referer.contains("/seriali/") || 
                      referer.contains("/anime/") || referer.contains("/cartoon") ||
                      rawHtml.contains("сезон") || rawHtml.contains("серія")
        if (isSeries) {
            possibleXfields.add("seria")
            possibleXfields.add("playlist")
            possibleXfields.add("series")
        } else {
            possibleXfields.add("playlist")
            possibleXfields.add("seria")
        }
        val distinctXfields = possibleXfields.distinct()

        // edittime
        val edittime = Regex("dle_edittime\\s*=\\s*'(\\d+)'")
            .find(rawHtml)
            ?.groupValues?.getOrNull(1)
            ?: Regex("news_id\\s*:\\s*'\\d+',\\s*time\\s*:\\s*'(\\d+)'").find(rawHtml)?.groupValues?.getOrNull(1)
            ?: (System.currentTimeMillis() / 1000).toString()

        for (xf in distinctXfields) {
            try {
                val response = uakinoApi.getPlaylist(newsId, xf, edittime)
                if (response.success) {
                    val seasons = parseAjaxResponseToSeasons(response.response.replace("\\/", "/"))
                    if (!seasons.isNullOrEmpty()) {
                        return seasons
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "AJAX request failed for xfield=$xf", e)
            }
        }

        return null
    }

    private suspend fun parseAjaxResponseToSeasons(htmlToParse: String): List<ProviderSeason>? {
        return try {
            if (htmlToParse.isBlank()) return null

            val d = Jsoup.parse(htmlToParse)
            val seasonTabs = d.select(".playlists-lists li")
            if (seasonTabs.isEmpty()) {
                return parseFlatPlaylistToSeasons(d)
            }

            val seasonsMap = mutableMapOf<Int, MutableList<ProviderEpisode>>()

            for (tab in seasonTabs) {
                currentCoroutineContext().ensureActive()
                val seasonId = tab.attr("data-id")
                val tabText = tab.text()

                val seasonNum = Regex("(\\d+)\\s*сезон", RegexOption.IGNORE_CASE)
                    .find(tabText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("\\d+").find(tabText)?.value?.toIntOrNull()
                    ?: (seasonsMap.size + 1)

                // Try to find videos for this specific season
                var items = d.select("#playlist-$seasonId li")
                if (items.isEmpty()) {
                    items = d.select(".playlists-videos li[data-id=\"$seasonId\"]")
                }
                if (items.isEmpty()) {
                    items = d.select("li[data-id=\"$seasonId\"]")
                }
                
                // Fallback 1: ID-based but without # symbol in selector
                if (items.isEmpty()) {
                    items = d.select("[data-id=\"$seasonId\"] li")
                }

                // Fallback 2: if we have multiple seasons but container is flat or ID doesn't match
                if (items.isEmpty() && (seasonTabs.size == 1 || seasonId.isBlank())) {
                    items = d.select(".playlists-videos li, .playlists-items li, .playlists-videos-list li")
                }
                
                // Fallback 3: look for active/current visible list if IDs are messy
                if (items.isEmpty()) {
                    items = d.select(".playlists-videos.active li, .playlists-videos[style*=\"display: block\"] li")
                }

                val eps = seasonsMap.getOrPut(seasonNum) { mutableListOf() }

                for (li in items) {
                    currentCoroutineContext().ensureActive()
                    var file = li.attr("data-file").ifEmpty { li.attr("data-src") }.ifEmpty { li.attr("data-url") }
                    if (file.isBlank()) continue
                    if (file.startsWith("//")) file = "https:$file"

                    val epText = li.text().trim()
                    val voice = li.attr("data-voice")

                    val epNum = Regex("(\\d+)\\s*серия", RegexOption.IGNORE_CASE)
                        .find(epText)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("\\d+").find(epText)?.value?.toIntOrNull()
                        ?: (eps.size + 1)

                    val epTitle = when {
                        voice.isNotEmpty() && !epText.contains(voice) -> "$epText ($voice)"
                        epText.isBlank() -> "Серія $epNum"
                        else -> epText
                    }

                    if (eps.none { it.url == file }) {
                        eps.add(ProviderEpisode(epNum, epTitle, file))
                    }
                }
            }

            // Group episodes by season if they were parsed from a messy list
            val finalSeasons = seasonsMap
                .filter { it.value.isNotEmpty() }
                .map { (num, eps) ->
                    ProviderSeason(
                        num,
                        eps.distinctBy { it.url }.sortedBy { it.number }
                    )
                }
                .sortedBy { it.number }
            
            if (finalSeasons.isNotEmpty()) {
                return finalSeasons
            }
            
            // If ID-based matching failed, try to treat the whole thing as one list
            val allEps = seasonsMap.values.flatten().distinctBy { it.url }
            if (allEps.isNotEmpty()) {
                return listOf(ProviderSeason(1, allEps.sortedBy { it.number }))
            }
            
            null
        } catch (e: Exception) {
            Log.e(tag, "AJAX parse error: ${e.message}")
            null
        }
    }

    private suspend fun parseFlatPlaylistToSeasons(doc: org.jsoup.nodes.Document): List<ProviderSeason>? {
        val episodes = mutableListOf<ProviderEpisode>()
        doc.select("li[data-file]").forEach { li ->
            currentCoroutineContext().ensureActive()
            var file = li.attr("data-file")
            if (file.isNotBlank()) {
                if (file.startsWith("//")) file = "https:$file"
                val epText = li.text().trim()
                val epNum = Regex("(\\d+)\\s*серия", RegexOption.IGNORE_CASE).find(epText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("\\d+").find(epText)?.value?.toIntOrNull()
                    ?: (episodes.size + 1)
                val epTitle = if (epText.isEmpty()) "Серія $epNum" else epText
                if (episodes.none { it.url == file }) {
                    episodes.add(ProviderEpisode(epNum, epTitle, file))
                }
            }
        }
        return if (episodes.isNotEmpty()) listOf(ProviderSeason(1, episodes.sortedBy { it.number })) else null
    }

    private suspend fun parsePlayerJsonSeasons(doc: org.jsoup.nodes.Document): List<ProviderSeason>? {
        val scripts = doc.select("script")
        for (script in scripts) {
            currentCoroutineContext().ensureActive()
            val content = script.data()
            if (!content.contains("Playerjs") && !content.contains("plr_config")) continue

            val jsonMatch = Regex("""(?:file|playlist|plr_config)\s*:\s*(\[.*?\]|\{.*?\})\s*[,;]""")
                .find(content)
            if (jsonMatch == null) continue

            val jsonData = jsonMatch.groupValues[1]
            val seasons = parsePlayerJsonToSeasons(jsonData)
            if (!seasons.isNullOrEmpty()) return seasons
        }
        return null
    }

    private suspend fun parsePlayerJsonToSeasons(jsonData: String): List<ProviderSeason>? {
        return try {
            if (jsonData.trim().startsWith("[")) {
                val array = org.json.JSONArray(jsonData)
                val episodes = mutableListOf<ProviderEpisode>()
                for (i in 0 until array.length()) {
                    currentCoroutineContext().ensureActive()
                    val item = array.getJSONObject(i)
                    val file = item.optString("file").ifEmpty { item.optString("url") }
                    if (file.isBlank()) continue
                    val title = item.optString("title").ifEmpty { "Серія ${i + 1}" }
                    episodes.add(ProviderEpisode(i + 1, title, file))
                }
                episodes.takeIf { it.isNotEmpty() }?.let { listOf(ProviderSeason(1, it)) }
            } else if (jsonData.trim().startsWith("{")) {
                val obj = JSONObject(jsonData)
                val folder = obj.optJSONArray("folder") ?: return null

                val seasons = mutableListOf<ProviderSeason>()
                for (i in 0 until folder.length()) {
                    currentCoroutineContext().ensureActive()
                    val sItem = folder.getJSONObject(i)
                    val sTitle = sItem.optString("title")
                    val sNum = Regex("\\d+").find(sTitle)?.value?.toIntOrNull() ?: (i + 1)

                    val episodes = mutableListOf<ProviderEpisode>()
                    val eFolder = sItem.optJSONArray("folder")
                    if (eFolder != null) {
                        for (j in 0 until eFolder.length()) {
                            currentCoroutineContext().ensureActive()
                            val eItem = eFolder.getJSONObject(j)
                            val eFile = eItem.optString("file")
                            if (eFile.isBlank()) continue
                            val eTitle = eItem.optString("title").ifEmpty { "Серія ${j + 1}" }
                            episodes.add(ProviderEpisode(j + 1, eTitle, eFile))
                        }
                    }

                    if (episodes.isNotEmpty()) seasons.add(ProviderSeason(sNum, episodes))
                }

                seasons.takeIf { it.isNotEmpty() }?.sortedBy { it.number }
            } else null
        } catch (e: Exception) {
            Log.e(tag, "Player JSON parse error: ${e.message}")
            null
        }
    }

    private fun detectEpisodeInfo(url: String): Pair<Int, Int>? {
        // s01e01, S01E01, s1e1
        Regex("""s(\d{1,3})[^0-9a-zA-Z]?e(\d{1,3})""", RegexOption.IGNORE_CASE)
            .find(url)
            ?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }

        // s01.esp01
        Regex("""s(\d{1,3})\.esp(\d{1,3})""", RegexOption.IGNORE_CASE)
            .find(url)
            ?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }

        // s01_01
        Regex("""s(\d{1,3})_(\d{1,3})""", RegexOption.IGNORE_CASE)
            .find(url)
            ?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }

        return null
    }
}
