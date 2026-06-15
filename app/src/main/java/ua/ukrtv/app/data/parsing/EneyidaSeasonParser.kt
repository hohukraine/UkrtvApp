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
 * Season-only parser for Eneyida.
 */
@Singleton
class EneyidaSeasonParser @Inject constructor(
    private val htmlHttpClient: HtmlHttpClient,
    private val eneyidaApi: ua.ukrtv.app.data.api.EneyidaApi
) : SeasonParser {

    private val tag = "EneyidaSeasonParser"
    private val baseUrl = "https://eneyida.tv/"
    private val providerName = "Eneyida"
    private val semaphore = Semaphore(3)

    override suspend fun parseSeries(pageUrl: String, html: String, referer: String): MediaSource? =
        withContext(Dispatchers.IO) {
            Log.d(tag, "Parsing series for $pageUrl")
            val doc = Jsoup.parse(html)

            val isActuallySeries = pageUrl.contains("/series/") || pageUrl.contains("/seriali/") ||
                pageUrl.contains("/serialy/") ||
                pageUrl.contains("/anime/") || pageUrl.contains("/anime-serials/") ||
                pageUrl.contains("/cartoon-series/") || pageUrl.contains("/multfilm-serials/")

            if (!isActuallySeries) return@withContext null

            // 1) Try to parse playlist directly from HTML (static)
            val staticPlaylist = parsePlaylistFromDoc(doc, pageUrl)
            if (staticPlaylist is MediaSource.Series) {
                Log.d(tag, "Found static playlist: ${staticPlaylist.seasons.size} seasons")
                return@withContext staticPlaylist
            }

            // 2) Try AJAX playlist
            val ajaxSeasons = parseAjaxSeasons(doc, html, referer.ifBlank { pageUrl })
            if (!ajaxSeasons.isNullOrEmpty()) {
                Log.d(tag, "Found AJAX playlist: ${ajaxSeasons.size} seasons")
                return@withContext MediaSource.Series(ajaxSeasons, pageUrl, providerName)
            }

            Log.w(tag, "No series data found for $pageUrl")
            null
        }

    private suspend fun parsePlaylistFromDoc(doc: org.jsoup.nodes.Document, pageUrl: String): MediaSource? {
        val seasonTabs = doc.select(".playlists-lists li")
        if (seasonTabs.isNotEmpty()) {
            val seasonsMap = mutableMapOf<Int, MutableList<ProviderEpisode>>()
            seasonTabs.forEach { tab ->
                currentCoroutineContext().ensureActive()
                val seasonId = tab.attr("data-id")
                val seasonNum = Regex("\\d+").find(tab.text())?.value?.toIntOrNull() ?: (seasonsMap.size + 1)
                
                val episodes = seasonsMap.getOrPut(seasonNum) { mutableListOf() }
                
                val container = doc.selectFirst("#playlist-$seasonId, .playlist-$seasonId")
                val items = container?.select("li") 
                    ?: doc.select(".playlists-videos li[data-id=\"$seasonId\"]")
                    ?: doc.select("li[data-id=\"$seasonId\"]")

                items.forEachIndexed { i, li ->
                    currentCoroutineContext().ensureActive()
                    val file = li.attr("data-file").ifEmpty { li.attr("data-src") }
                    if (file.isNotEmpty()) {
                        val title = li.text().trim().ifEmpty { "Серія ${i + 1}" }
                        val url = if (file.startsWith("//")) "https:$file" else file
                        if (episodes.none { it.url == url }) {
                            episodes.add(ProviderEpisode(i + 1, title, url))
                        }
                    }
                }
            }
            if (seasonsMap.isNotEmpty()) {
                val seasons = seasonsMap.map { (num, eps) ->
                    ProviderSeason(num, eps.sortedBy { it.number })
                }.sortedBy { it.number }
                return MediaSource.Series(seasons, pageUrl, providerName)
            }
        }

        val episodes = mutableListOf<ProviderEpisode>()
        doc.select("li[data-file], li[data-src]").forEachIndexed { i, li ->
            currentCoroutineContext().ensureActive()
            val file = li.attr("data-file").ifEmpty { li.attr("data-src") }
            if (file.isNotEmpty()) {
                val title = li.text().trim().ifEmpty { "Серія ${i + 1}" }
                val url = if (file.startsWith("//")) "https:$file" else file
                if (episodes.none { it.url == url }) {
                    episodes.add(ProviderEpisode(i + 1, title, url))
                }
            }
        }

        if (episodes.size > 1) {
            return MediaSource.Series(listOf(ProviderSeason(1, episodes.sortedBy { it.number })), pageUrl, providerName)
        }
        return null
    }

    private suspend fun parseAjaxSeasons(
        doc: org.jsoup.nodes.Document,
        rawHtml: String,
        referer: String
    ): List<ProviderSeason>? {
        val playlistDiv = doc.selectFirst(".playlists-ajax") ?: doc.selectFirst("[data-news_id]") ?: return null
        val newsId = playlistDiv.attr("data-news_id")
        if (newsId.isBlank()) return null

        var detectedXfname = playlistDiv.attr("data-xfname").ifEmpty { playlistDiv.attr("data-xfield") }
        
        val possibleXfields = mutableListOf<String>()
        if (detectedXfname.isNotBlank()) possibleXfields.add(detectedXfname)
        
        val isSeries = referer.contains("/series/") || referer.contains("/serialy/") || 
                      referer.contains("/seriali/") || referer.contains("/anime/") || 
                      referer.contains("/cartoon")
        if (isSeries) {
            possibleXfields.add("seria")
            possibleXfields.add("playlist")
        } else {
            possibleXfields.add("playlist")
            possibleXfields.add("seria")
        }
        val distinctXfields = possibleXfields.distinct()

        val edittimeMatch = Regex("dle_edittime\\s*=\\s*'(\\d+)'").find(rawHtml)
        val edittime = edittimeMatch?.groupValues?.get(1) ?: (System.currentTimeMillis() / 1000).toString()

        for (xf in distinctXfields) {
            try {
                val response = try {
                    eneyidaApi.getPlaylistRe(newsId, xf, edittime)
                } catch (e: Exception) {
                    eneyidaApi.getPlaylistGet(newsId, xf, edittime)
                }

                if (response.success) {
                    val seasons = parseAjaxResponseToSeasons(response.response.replace("\\/", "/"), referer)
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

    private suspend fun parseAjaxResponseToSeasons(htmlToParse: String, pageUrl: String): List<ProviderSeason>? {
        return try {
            if (htmlToParse.isBlank()) return null
            val doc = Jsoup.parse(htmlToParse)
            val media = parsePlaylistFromDoc(doc, pageUrl)
            if (media is MediaSource.Series) media.seasons else null
        } catch (e: Exception) {
            Log.e(tag, "AJAX Error: ${e.message}")
            null
        }
    }

    private fun detectEpisodeInfo(url: String): Pair<Int, Int>? {
        Regex("""s(\d{1,3})[^0-9a-zA-Z]?e(\d{1,3})""", RegexOption.IGNORE_CASE)
            .find(url)
            ?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }

        Regex("""s(\d{1,3})\.esp(\d{1,3})""", RegexOption.IGNORE_CASE)
            .find(url)
            ?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }

        Regex("""s(\d{1,3})_(\d{1,3})""", RegexOption.IGNORE_CASE)
            .find(url)
            ?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }

        return null
    }
}
