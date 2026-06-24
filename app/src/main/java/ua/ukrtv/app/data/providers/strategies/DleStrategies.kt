package ua.ukrtv.app.data.providers.strategies

import kotlinx.coroutines.*
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.providers.DleParser
import ua.ukrtv.app.data.providers.DleResolutionUtils
import ua.ukrtv.app.data.providers.MediaSource
import ua.ukrtv.app.data.providers.ProviderSeason
import ua.ukrtv.app.data.providers.ProviderEpisode
import ua.ukrtv.app.data.streaming.UnifiedStreamProvider
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.util.AppLogger
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.FormBody
import com.google.gson.Gson

data class DleConfig(
    val profile: ua.ukrtv.app.data.providers.DleProviderProfile,
    val parsingStrategy: ParsingStrategy,
    val resolutionChain: ResolutionChain
)

interface ParsingStrategy {
    fun extractMovies(html: String): List<Movie>
    fun extractMovies(element: org.jsoup.nodes.Element): List<Movie>
    fun extractDetail(html: String, url: String): MovieDetail
    fun extractDetail(doc: org.jsoup.nodes.Document, url: String): MovieDetail
    fun extractSearch(html: String): List<Movie>
}

class JsoupParsingStrategy(private val parser: DleParser) : ParsingStrategy {
    override fun extractMovies(html: String) = parser.parseList(html)
    override fun extractMovies(element: org.jsoup.nodes.Element) = parser.parseList(element)
    override fun extractDetail(html: String, url: String) = parser.parseDetail(html, url)
    override fun extractDetail(doc: org.jsoup.nodes.Document, url: String) = parser.parseDetail(doc, url)
    override fun extractSearch(html: String) = parser.parseSearch(html)
}

data class ResolutionContext(
    val url: String,
    val html: String,
    val userHash: String,
    val contentType: ContentType,
    val client: HtmlHttpClient,
    val streamProvider: UnifiedStreamProvider,
    val config: DleConfig,
    val isDeep: Boolean = true
) {
    val doc: Document by lazy { Jsoup.parse(html, url) }
    
    val newsId: String by lazy {
        doc.selectFirst("input[name=news_id], #news_id, input[name=post_id]")?.attr("value")
            ?: Regex("""news_id\s*[:=]\s*["']?(\d+)""").find(html)?.groupValues?.get(1)
            ?: Regex("""dle_news_id\s*=\s*['"]?(\d+)""").find(html)?.groupValues?.get(1)
            ?: Regex("""/(\d+)-""").find(url)?.groupValues?.get(1) ?: ""
    }

    val defaultSeason: Int? by lazy { DleResolutionUtils.extractSeasonNum(url) ?: DleResolutionUtils.extractSeasonNum(doc.title()) }

    val otherSeasons: List<Pair<Int, String>> by lazy {
        try {
            val targetContainers = doc.select(".seasons, .franchise-list, .serial-series")
            val source = if (targetContainers.isNotEmpty()) targetContainers else doc.select(".video-tabs, .player-tabs, .tabs-sel")

            if (source.isNotEmpty()) {
                source.select("a[href]").mapNotNull { a ->
                    val sNum = DleResolutionUtils.extractSeasonNum(a.text()) ?: return@mapNotNull null
                    if (sNum > 50) return@mapNotNull null
                    sNum to a.attr("abs:href")
                }.distinctBy { it.second }.sortedBy { it.first }
            } else if (contentType == ContentType.SERIES) {
                doc.select("a[href*='-sezon']").filter { a ->
                    a.parents().none { p ->
                        val cls = (p.className() + " " + p.id()).lowercase()
                        cls.contains("side") || cls.contains("sidebar") || cls.contains("related")
                    }
                }.mapNotNull { a ->
                    val sNum = DleResolutionUtils.extractSeasonNum(a.text()) ?: return@mapNotNull null
                    if (sNum > 50) return@mapNotNull null
                    sNum to a.attr("abs:href")
                }.distinctBy { it.second }.sortedBy { it.first }
            } else {
                emptyList()
            }
        } catch (_: Exception) { emptyList() }
    }
}

interface MediaResolutionStrategy {
    suspend fun resolve(context: ResolutionContext): MediaSource?
}

class ResolutionChain(private val strategies: List<MediaResolutionStrategy>) {
    suspend fun resolve(context: ResolutionContext): MediaSource? {
        if (context.config.profile.name == "Eneyida") {
            val media = DleResolutionUtils.findMediaUrlsInText(context.html)
            if (media.isNotEmpty()) {
                AppLogger.d("ResolutionChain", "Eneyida direct stream found: ${media.first()}")
                return MediaSource.Movie(media.first(), emptyList(), context.url, context.config.profile.name)
            }
        }

        for (strategy in strategies) {
            try {
                AppLogger.d("ResolutionChain", "Trying strategy: ${strategy::class.simpleName}")
                strategy.resolve(context)?.let {
                    AppLogger.d("ResolutionChain", "Strategy succeeded: ${it.primaryUrl?.take(50)}")
                    return it
                }
            } catch (ex: Exception) {
                AppLogger.w("ResolutionChain", "Strategy ${strategy::class.simpleName} failed: ${ex.message}")
            }
        }

        if (context.isDeep && context.otherSeasons.isNotEmpty()) {
            val allSeasons = mutableListOf<ProviderSeason>()

            val semaphore = Semaphore(4)
            val deferredList = context.otherSeasons.take(12).map { (num, sUrl) ->
                coroutineScope {
                    async(Dispatchers.IO) {
                        if (allSeasons.any { it.number == num }) return@async null
                        semaphore.withPermit {
                            try {
                                val sHtml = context.client.getHtml(sUrl) ?: return@withPermit null
                                val media = DleResolutionUtils.findMediaUrlsInText(sHtml)
                                if (media.isNotEmpty()) {
                                    return@withPermit listOf(ProviderSeason(num, listOf(ProviderEpisode(1, "Серія", media.first()))))
                                }
                                // Try AJAX playlist for Uakino season pages
                                val sDoc = Jsoup.parse(sHtml)
                                val playlistDiv = sDoc.selectFirst(".playlists-ajax, [data-news_id]")
                                if (playlistDiv != null) {
                                    val newsId = playlistDiv.attr("data-news_id")
                                    if (newsId.isNotBlank()) {
                                        val ajaxUrl = "${context.config.profile.baseUrl}engine/ajax/playlists.php"
                                        val ajaxBody = FormBody.Builder()
                                            .add("news_id", newsId)
                                            .add("xfield", "playlist")
                                            .build()
                                        val ajaxResp = context.client.postHtml(ajaxUrl, ajaxBody, sUrl, isAjax = true)
                                        if (ajaxResp != null) {
                                            val ajaxJson = try { Gson().fromJson(ajaxResp, Map::class.java) } catch (_: Exception) { null }
                                            if (ajaxJson?.get("success") == true) {
                                                val playlistHtml = (ajaxJson["response"] as? String) ?: ""
                                                val pDoc = Jsoup.parse(playlistHtml)
                                                val eps = mutableListOf<ProviderEpisode>()
                                                for (li in pDoc.select(".playlists-videos li, li[data-file]")) {
                                                    var file = li.attr("data-file").ifEmpty { li.attr("data-src") }.ifEmpty { li.attr("data-url") }
                                                    if (file.isBlank()) continue
                                                    if (file.startsWith("//")) file = "https:$file"
                                                    val epNum = Regex("""(\d+)""").find(li.text())?.groupValues?.get(1)?.toIntOrNull()
                                                        ?: continue
                                                    eps.add(ProviderEpisode(epNum, li.text().trim(), file))
                                                }
                                                if (eps.isNotEmpty()) {
                                                    return@withPermit listOf(ProviderSeason(num, eps.sortedBy { it.number }))
                                                }
                                            }
                                        }
                                    }
                                }
                                null
                            } catch (e: Exception) {
                                AppLogger.w("ResolutionChain", "Deep fetch failed for S$num: ${e.message}")
                                null
                            }
                        }
                    }
                }
            }

            deferredList.awaitAll().filterNotNull().flatten().let { allSeasons.addAll(it) }

            if (allSeasons.isNotEmpty()) {
                val merged = allSeasons.groupBy { it.number }
                    .map { (num, list) ->
                        ProviderSeason(num, list.flatMap { it.episodes }.distinctBy { it.url }.sortedBy { it.number })
                    }.sortedBy { it.number }
                AppLogger.d("ResolutionChain", "Deep resolution found ${merged.size} seasons total")
                return MediaSource.Series(merged, context.url, context.config.profile.name)
            }
        }
        return null
    }
}

class AjaxPlaylistResolutionStrategy : MediaResolutionStrategy {
    override suspend fun resolve(context: ResolutionContext): MediaSource? {
        val playlistDiv = context.doc.selectFirst(".playlists-ajax, [data-news_id]") ?: return null
        val newsId = playlistDiv.attr("data-news_id")
        if (newsId.isBlank()) return null

        AppLogger.d("AjaxPlaylistResolutionStrategy", "Found news_id=$newsId")

        suspend fun fetchSeasonEpisodes(seasonUrl: String, seasonNum: Int): ProviderSeason? {
            val sHtml = context.client.getHtml(seasonUrl, context.url) ?: return null
            val sDoc = Jsoup.parse(sHtml)
            val sPlaylistDiv = sDoc.selectFirst(".playlists-ajax, [data-news_id]") ?: return null
            val sNewsId = sPlaylistDiv.attr("data-news_id")
            if (sNewsId.isBlank()) return null

            val ajaxUrl = "${context.config.profile.baseUrl}engine/ajax/playlists.php"
            val ajaxBody = FormBody.Builder()
                .add("news_id", sNewsId)
                .add("xfield", "playlist")
                .build()
            val ajaxResp = context.client.postHtml(ajaxUrl, ajaxBody, seasonUrl, isAjax = true) ?: return null
            val ajaxJson = try { Gson().fromJson(ajaxResp, Map::class.java) } catch (_: Exception) { return null }
            if (ajaxJson["success"] != true) return null

            val playlistHtml = (ajaxJson["response"] as? String) ?: return null
            val pDoc = Jsoup.parse(playlistHtml)
            val eps = mutableListOf<ProviderEpisode>()
            val seen = mutableSetOf<String>()
            for (li in pDoc.select(".playlists-videos li, li[data-file]")) {
                var file = li.attr("data-file").ifEmpty { li.attr("data-src") }.ifEmpty { li.attr("data-url") }
                if (file.isBlank()) continue
                if (file.startsWith("//")) file = "https:$file"
                val epNum = Regex("""(\d+)""").find(li.text())?.groupValues?.get(1)?.toIntOrNull() ?: continue
                if (!seen.add(file)) continue
                val voice = li.attr("data-voice")
                val epTitle = if (voice.isNotEmpty()) "${li.text().trim()} ($voice)" else li.text().trim()
                eps.add(ProviderEpisode(epNum, epTitle, file))
            }
            if (eps.isEmpty()) return null
            return ProviderSeason(seasonNum, eps.sortedBy { it.number })
        }

        val url = "${context.config.profile.baseUrl}engine/ajax/playlists.php"
        val body = FormBody.Builder()
            .add("news_id", newsId)
            .add("xfield", "playlist")
            .build()

        val response = context.client.postHtml(url, body, context.url, isAjax = true) ?: return null
        val json = try { Gson().fromJson(response, Map::class.java) } catch (_: Exception) { return null }
        if (json["success"] != true) return null

        val responseHtml = (json["response"] as? String) ?: return null
        val doc = Jsoup.parse(responseHtml)

        val allSeasons = mutableListOf<ProviderSeason>()

        // Parse current season episodes
        val currentSeasonNum = context.defaultSeason ?: 1
        val curEps = mutableListOf<ProviderEpisode>()
        val seen = mutableSetOf<String>()
        for (li in doc.select(".playlists-videos li, li[data-file]")) {
            var file = li.attr("data-file").ifEmpty { li.attr("data-src") }.ifEmpty { li.attr("data-url") }
            if (file.isBlank()) continue
            if (file.startsWith("//")) file = "https:$file"
            val title = li.text().trim()
            val epNum = Regex("""(\d+)""").find(title)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (!seen.add(file)) continue
            val voice = li.attr("data-voice")
            val epTitle = if (voice.isNotEmpty() && !title.contains(voice)) "$title ($voice)" else title
            curEps.add(ProviderEpisode(epNum, epTitle, file))
        }
        if (curEps.isNotEmpty()) {
            allSeasons.add(ProviderSeason(currentSeasonNum, curEps.sortedBy { it.number }))
        }

        // Fetch additional seasons from otherSeasons
        if (context.otherSeasons.isNotEmpty()) {
            for ((sNum, sUrl) in context.otherSeasons) {
                if (allSeasons.any { it.number == sNum }) continue
                try {
                    fetchSeasonEpisodes(sUrl, sNum)?.let { allSeasons.add(it) }
                } catch (e: Exception) {
                    AppLogger.w("AjaxPlaylistResolutionStrategy", "Failed to fetch S$sNum: ${e.message}")
                }
            }
        }

        if (allSeasons.isNotEmpty()) {
            AppLogger.d("AjaxPlaylistResolutionStrategy", "Found ${allSeasons.size} seasons total")
            return MediaSource.Series(allSeasons.sortedBy { it.number }, context.url, context.config.profile.name)
        }
        return null
    }
}

class IframeResolutionStrategy : MediaResolutionStrategy {
    override suspend fun resolve(context: ResolutionContext): MediaSource? {
        val iframes = context.doc.select("iframe").mapNotNull { ifr ->
            val src = ifr.attr("abs:data-src").ifEmpty { ifr.attr("abs:src") }
            if (src.isEmpty() || src.contains("youtube") || src.contains("facebook")) null else src
        }
        
        AppLogger.d("IframeResolutionStrategy", "Found ${iframes.size} iframes")

        // 🚀 FIX: For Eneyida, filter out trailer iframe (vid/...?tr=1), but use it as fallback
        val filtered = if (context.config.profile.name == "Eneyida") {
            iframes.filterNot { it.contains("vid/") && it.contains("tr=1") }
        } else {
            iframes
        }
        
        // If all iframes filtered out (trailer only), use the first one anyway
        val effectiveIframes = if (filtered.isEmpty() && iframes.isNotEmpty()) iframes else filtered
        
        AppLogger.d("IframeResolutionStrategy", "After filter: ${effectiveIframes.size} iframes to try")

        // For MOVIE or Eneyida, try all iframes since we need at least one
        val iframeLimit = if (context.contentType == ContentType.MOVIE || context.config.profile.name == "Eneyida") Int.MAX_VALUE else 2
        
        for (src in effectiveIframes.take(iframeLimit)) {
            AppLogger.d("IframeResolutionStrategy", "Trying iframe: $src")
            
            // First, try to extract m3u8/mp4/webm URLs from iframe content (like Python)
            try {
                val iframeResp = context.client.getHtml(src, context.url) ?: continue
                val media = DleResolutionUtils.findMediaUrlsInText(iframeResp)
                if (media.isNotEmpty()) {
                    AppLogger.d("IframeResolutionStrategy", "Found ${media.size} m3u8 in iframe: ${media.first()}")
                    
                    if (context.config.profile.name == "Eneyida") {
                        // First: try to extract structured JSON playlist from iframe
                        val jsonPlaylist = try {
                            Regex("""file\s*:\s*['"](\[.*\])['"]""", RegexOption.DOT_MATCHES_ALL)
                                .find(iframeResp)?.groupValues?.get(1)
                        } catch (_: Exception) { null }
                        
                        if (jsonPlaylist != null) {
                            try {
                                val gson = com.google.gson.Gson()
                                val list = gson.fromJson(jsonPlaylist, List::class.java)
                                if (list != null && list.isNotEmpty()) {
                                    val seasons = mutableListOf<ProviderSeason>()
                                    for (item in list) {
                                        val m = item as? Map<*, *> ?: continue
                                        val sNum = DleResolutionUtils.extractSeasonNum(
                                            (m["title"] as? String) ?: ""
                                        ) ?: (seasons.size + 1)
                                        val voiceFolders = m["folder"] as? List<*> ?: continue
                                        
                                        val allEpisodes = mutableListOf<ProviderEpisode>()
                                        val seen = mutableSetOf<Int>()
                                        for (voiceItem in voiceFolders) {
                                            val vm = voiceItem as? Map<*, *> ?: continue
                                            val eps = vm["folder"] as? List<*> ?: continue
                                            for (ep in eps) {
                                                val em = ep as? Map<*, *> ?: continue
                                                val url = (em["file"] as? String) ?: continue
                                                val title = (em["title"] as? String) ?: ""
                                                val epNum = Regex("""(\d+)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
                                                    ?: continue
                                                if (seen.add(epNum)) {
                                                    allEpisodes.add(ProviderEpisode(epNum, title, url))
                                                }
                                            }
                                        }
                                        if (allEpisodes.isNotEmpty()) {
                                            seasons.add(ProviderSeason(sNum, allEpisodes.sortedBy { it.number }))
                                        }
                                    }
                                    if (seasons.isNotEmpty()) {
                                        AppLogger.d("IframeResolutionStrategy", "Eneyida JSON: ${seasons.size} seasons")
                                        return MediaSource.Series(seasons.sortedBy { it.number }, context.url, context.config.profile.name)
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                        
                        // Fallback: extract episode info from URLs
                        data class TempEp(val s: Int, val e: Int, val url: String)
                        val tempEpisodes = media.mapNotNull { url ->
                            val hdvbMatch = Regex("""s(\d+)e(\d+)""", RegexOption.IGNORE_CASE).find(url)
                            if (hdvbMatch != null) {
                                return@mapNotNull TempEp(
                                    hdvbMatch.groupValues[1].toInt(),
                                    hdvbMatch.groupValues[2].toInt(),
                                    url
                                )
                            }
                            
                            val parts = url.split("/")
                            val indexIdx = parts.indexOfLast { it.contains("index.m3u8") }
                            if (indexIdx >= 2) {
                                val eNum = parts[indexIdx - 1].toIntOrNull()
                                val sNum = parts[indexIdx - 2].toIntOrNull()
                                if (eNum != null && sNum != null) {
                                    if (sNum > 100 && indexIdx >= 3) {
                                        val idPart = parts[indexIdx - 1]
                                        if (idPart.length > 5) {
                                            val s = parts[indexIdx - 3].toIntOrNull()
                                            val e = parts[indexIdx - 2].toIntOrNull()
                                            if (s != null && e != null && s < 100) return@mapNotNull TempEp(s, e, url)
                                        }
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
                            
                            AppLogger.d("IframeResolutionStrategy", "Eneyida grouped: ${groupedSeasons.size} seasons")
                            return MediaSource.Series(groupedSeasons, context.url, context.config.profile.name)
                        }
                        
                        if (media.size > 1) {
                            val episodes = media.mapIndexed { idx, url ->
                                ProviderEpisode(idx + 1, "Серія ${idx + 1}", url)
                            }
                            val seasonNum = context.defaultSeason ?: 1
                            return MediaSource.Series(listOf(ProviderSeason(seasonNum, episodes)), context.url, context.config.profile.name)
                        }
                    }
                    
                    return MediaSource.Movie(media.first(), emptyList(), context.url, context.config.profile.name)
                }
            } catch (ex: Exception) {
                AppLogger.w("IframeResolutionStrategy", "Iframe fetch failed: ${ex.message}")
            }
            
            // Check for known player domains (like Python script) - return iframe URL as fallback
            if (listOf("hdvb", "ashdi", "vidmoly", "mcloud", "vidsrc").any { src.contains(it) }) {
                if (src.length > 5 && !src.contains("<") && !src.contains(">")) {
                    AppLogger.d("IframeResolutionStrategy", "Found known player (no direct m3u8): $src")
                    return MediaSource.Movie(src, emptyList(), context.url, context.config.profile.name)
                }
            }
        }
        return null
    }
}
