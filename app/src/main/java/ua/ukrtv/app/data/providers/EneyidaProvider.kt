package ua.ukrtv.app.data.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.repository.CatalogRepository
import ua.ukrtv.app.data.repository.SessionRepository
import ua.ukrtv.app.util.AppLogger

class EneyidaProvider(
    htmlHttpClient: HtmlHttpClient,
    sessionRepository: SessionRepository,
    catalogRepository: CatalogRepository
) : DleProviderBase(htmlHttpClient, sessionRepository, catalogRepository, EneyidaProfile) {

    companion object {
        private val FILE_JSON_REGEX = Regex("""file\s*:\s*['"](\[.{0,50000})['"]""", RegexOption.DOT_MATCHES_ALL)
    }

    override val name: String = "Eneyida"
    override val baseUrl: String = "https://eneyida.tv/"
    override val brandColor: String = "#31C469"

    override fun supportsUrl(url: String) = url.contains("eneyida.tv")

    override suspend fun resolveSeriesContent(
        html: String, pageUrl: String, doc: Document,
        season: Int?, episode: Int?, isDeep: Boolean
    ): MediaSource? {
        val iframes = extractIframes(doc)

        if (iframes.isEmpty()) return null

        for (src in iframes) {
            try {
                val iframeResp = htmlHttpClient.getHtml(src, pageUrl) ?: continue

                val jsonPlaylist = try {
                    val fileMatch = FILE_JSON_REGEX.find(iframeResp)?.groupValues?.get(1)
                    fileMatch?.let { SeriesPlaylistParser.extractBalancedJson(it) }
                } catch (e: Exception) {
                    AppLogger.w("$name:JsonPlaylist", "Extraction failed: ${e.message}")
                    null
                }

                if (jsonPlaylist != null) {
                    try {
                        val parsed = SeriesPlaylistParser.parseJsonPlaylist(jsonPlaylist, pageUrl, name)
                        if (parsed != null) return parsed
                    } catch (e: Exception) {
                        AppLogger.w(name, "JSON playlist parse failed: ${e.message}")
                    }
                }

                val media = DleResolutionUtils.findMediaUrlsInText(iframeResp)
                if (media.isNotEmpty()) {
                    val series = SeriesPlaylistParser.parseUrlBasedSeries(media, pageUrl, name)
                    if (series != null) return series
                }
            } catch (e: Exception) {
                AppLogger.w("$name:SeriesIframe", "Iframe resolution failed: ${e.message}")
            }
        }

        if (isDeep) {
            val otherSeasons = resolveOtherSeasons(doc, pageUrl)
            if (otherSeasons.isNotEmpty()) {
                return deepResolveSeasons(otherSeasons, html, pageUrl)
            }
        }

        return null
    }

    private suspend fun deepResolveSeasons(
        otherSeasons: List<Pair<Int, String>>,
        html: String, pageUrl: String
    ): MediaSource? {
        val allSeasons = mutableListOf<ProviderSeason>()
        val semaphore = Semaphore(3)

        val results: List<List<ProviderSeason>?> = coroutineScope {
            otherSeasons.take(12).map { (num, sUrl) ->
                async(Dispatchers.IO) {
                    val result: List<ProviderSeason>? = semaphore.withPermit {
                        try {
                            AppLogger.d(name, "Deep fetching S$num: $sUrl")
                            val sHtml = htmlHttpClient.getHtml(sUrl) ?: return@withPermit null
                            val media = DleResolutionUtils.findMediaUrlsInText(sHtml)
                            if (media.isNotEmpty()) {
                                val sDoc = Jsoup.parse(sHtml)
                                val iframes = extractIframes(sDoc)
                                for (src in iframes) {
                                    try {
                                        val iframeResp = htmlHttpClient.getHtml(src, sUrl) ?: continue
                                        val jsonPlaylist = try {
                                            val fileMatch = FILE_JSON_REGEX.find(iframeResp)?.groupValues?.get(1)
                                            fileMatch?.let { SeriesPlaylistParser.extractBalancedJson(it) }
                                        } catch (e: Exception) {
                                            AppLogger.w("$name:DeepJson", "JSON extraction failed: ${e.message}")
                                            null
                                        }
                                        if (jsonPlaylist != null) {
                                            val parsed = SeriesPlaylistParser.parseJsonPlaylist(jsonPlaylist, sUrl, name)
                                            if (parsed != null) return@withPermit parsed.seasons
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.w("Eneyida:DeepIframe", "Iframe failed: ${e.message}")
                                    }
                                }
                                return@withPermit listOf(ProviderSeason(num, listOf(ProviderEpisode(1, "Серія", media.first()))))
                            }
                            null
                        } catch (e: Exception) {
                            AppLogger.w("$name:DeepResolve", "Failed S$num: ${e.message}")
                            null
                        }
                    }
                    result
                }
            }.awaitAll()
        }

        results.forEach { seasonList ->
            seasonList?.forEach { s ->
                if (allSeasons.none { it.number == s.number }) allSeasons.add(s)
            }
        }

        return mergeSeasons(allSeasons, pageUrl)
    }
}
