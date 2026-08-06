package ua.ukrtv.app.data.providers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.repository.CatalogRepository
import ua.ukrtv.app.data.repository.SessionRepository
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject

class UaflixProvider @Inject constructor(
    htmlHttpClient: HtmlHttpClient,
    sessionRepository: SessionRepository,
    catalogRepository: CatalogRepository,
) : DleProviderBase(htmlHttpClient, sessionRepository, catalogRepository, UaflixProfile) {

    override val name = "UAFLIX"
    override val baseUrl: String = UaflixProfile.baseUrl
    override val brandColor: String = UaflixProfile.brandColor

    override fun supportsUrl(url: String): Boolean =
        url.contains("uafix.net") || url.contains("uaflix.")

    override suspend fun resolveSeriesContent(
        html: String, pageUrl: String, doc: Document,
        season: Int?, episode: Int?, isDeep: Boolean
    ): MediaSource? {
        if (pageUrl.contains("-episode-")) {
            val iframeSrc = extractIframes(doc).firstOrNull()
            if (iframeSrc != null) {
                // Try to resolve the real stream directly from the iframe (e.g. zetvideo Playerjs config)
                val iframeHtml = try {
                    htmlHttpClient.getHtml(iframeSrc, pageUrl)
                } catch (e: Exception) {
                    AppLogger.w("UAFLIX", "Episode iframe fetch failed: ${e.message}")
                    null
                }
                val media = iframeHtml?.let { DleResolutionUtils.findMediaUrlsInText(it) }.orEmpty()
                if (media.isNotEmpty()) {
                    val best = selectBestMediaUrl(media) ?: media.first()
                    AppLogger.d("UAFLIX", "Episode resolved to stream: $best (${media.size} links)")
                    return MediaSource.Movie(best, media.filter { it != best }, pageUrl, name)
                }
                AppLogger.d("UAFLIX", "Episode iframe has no inline media, delegating to resolver: $iframeSrc")
                return MediaSource.Movie(iframeSrc, referer = pageUrl, providerName = name)
            }
            AppLogger.d("UAFLIX", "No iframe found on episode page: $pageUrl")
            return null
        }

        // Find episode links like /serials/poganij-prokuror/season-01-episode-01/
        val seasonsMap = parseEpisodeLinks(doc).toMutableMap()
        if (seasonsMap.isEmpty()) return null

        // The serial page only lists a subset of episodes (hero widget + recent grid) and can
        // contain duplicate links (hero + "watch" button), so a target episode may be missing
        // and the list may hold duplicate numbers. Complete every season from its own
        // /sezon-N/ page, which contains the full episode list.
        val needsCompleteList = isDeep ||
            (season != null && episode != null && seasonsMap[season]?.any { it.number == episode } != true)
        if (needsCompleteList) {
            val seasonPageUrls = seasonPageLinks(doc, pageUrl)
                .filter { (_, sUrl) -> sUrl != pageUrl }
                .let { links ->
                    if (season != null) links.filter { (sNum, _) -> sNum == season } else links
                }
            if (seasonPageUrls.isNotEmpty()) {
                val completed = coroutineScope {
                    seasonPageUrls
                        .mapIndexed { idx, (sNum, sUrl) ->
                            async(Dispatchers.IO) {
                                if (idx > 0) delay(Constants.SERIES_FETCH_STAGGER_MS * idx)
                                withTimeoutOrNull(Constants.PER_SEASON_FETCH_TIMEOUT_MS) {
                                    try {
                                        val sHtml = htmlHttpClient.getHtml(sUrl, pageUrl, skipRateLimitRetry = true)
                                        if (sHtml == null) {
                                            AppLogger.w("UAFLIX", "Failed to fetch season page S$sNum")
                                            null
                                        } else {
                                            val sDoc = Jsoup.parse(sHtml, sUrl)
                                            sNum to parseEpisodeLinks(sDoc)[sNum]
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        AppLogger.w("UAFLIX", "Failed to fetch season page S$sNum: ${e.message}")
                                        null
                                    }
                                }
                            }
                        }
                        .awaitAll()
                        .filterNotNull()
                }
                completed.forEach { (sNum, eps) ->
                    if (eps != null) seasonsMap[sNum] = eps
                }
            }
        }

        val providerSeasons = seasonsMap.map { (num, eps) ->
            // Drop duplicate numbers (hero + watch button share the same episode).
            val unique = eps.distinctBy { it.number }.sortedBy { it.number }
            ProviderSeason(num, unique)
        }.sortedBy { it.number }

        return MediaSource.Series(providerSeasons, pageUrl, name)
    }

    private fun parseEpisodeLinks(doc: Document): Map<Int, List<ProviderEpisode>> {
        val seenHrefs = mutableSetOf<String>()
        val seasonsMap = mutableMapOf<Int, MutableList<ProviderEpisode>>()

        doc.select("a[href*='season-'][href*='-episode-']").forEach { link ->
            val href = link.attr("abs:href")
            if (!seenHrefs.add(href)) return@forEach

            // Extract season and episode from URL: .../season-01-episode-08/
            val seasonMatch = Regex("""season-(\d+)""").find(href)
            val episodeMatch = Regex("""episode-(\d+)""").find(href)

            val sNum = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val eNum = episodeMatch?.groupValues?.get(1)?.toIntOrNull()

            if (eNum != null && eNum > 0) {
                seasonsMap.getOrPut(sNum) { mutableListOf() }.add(
                    ProviderEpisode(eNum, "Серія $eNum", href)
                )
            }
        }

        return seasonsMap
    }

    private fun seasonPageLinks(doc: Document, pageUrl: String): List<Pair<Int, String>> {
        val slug = pageUrl.trimEnd('/').substringAfterLast("/").substringBefore("-sezon")
        if (slug.isEmpty()) return emptyList()
        return doc.select("a[href*='sezon-']").mapNotNull { link ->
            val href = link.attr("abs:href")
            if (!href.contains("/$slug/")) return@mapNotNull null
            val sNum = Regex("""sezon-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
            if (sNum != null && sNum in 1..50) sNum to href else null
        }.distinctBy { it.second }
    }
}
