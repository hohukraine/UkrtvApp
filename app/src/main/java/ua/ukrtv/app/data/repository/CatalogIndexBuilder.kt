package ua.ukrtv.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import ua.ukrtv.app.Constants
import ua.ukrtv.app.data.local.dao.CatalogIndexDao
import ua.ukrtv.app.data.local.entity.CatalogIndexEntity
import ua.ukrtv.app.data.network.HtmlHttpClient
import ua.ukrtv.app.data.providers.ContentUtils
import ua.ukrtv.app.data.providers.DleProviderProfile
import ua.ukrtv.app.data.providers.EneyidaProfile
import ua.ukrtv.app.data.providers.UakinoProfile
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject

data class CatalogSource(
    val path: String,
    val contentType: String
)

data class CatalogBuildResult(
    val provider: String,
    val itemsInserted: Int,
    val pagesScanned: Int,
    val errors: Int
)

class CatalogIndexBuilder @Inject constructor(
    private val htmlHttpClient: HtmlHttpClient,
    private val catalogDao: CatalogIndexDao
) {
    private val maxPagesPerCategory = 300

    suspend fun buildForProvider(profile: DleProviderProfile, sources: List<CatalogSource>): CatalogBuildResult =
        withContext(Dispatchers.IO) {
            var totalItems = 0
            var totalPages = 0
            var totalErrors = 0

            catalogDao.deleteByProvider(profile.name)

            for (source in sources) {
                var page = 1
                var hasMore = true
                val pageItems = mutableListOf<CatalogIndexEntity>()

                while (hasMore && page <= maxPagesPerCategory) {
                    val pageUrl = buildPageUrl(profile.baseUrl, source.path, page)
                    try {
                        val html = htmlHttpClient.getHtml(pageUrl, profile.baseUrl) ?: ""
                        if (html.isBlank()) { hasMore = false; break }

                        val items = parseCategoryPage(html, profile.baseUrl, source.contentType, profile)
                        if (items.isEmpty()) { hasMore = false; break }

                        pageItems.addAll(items)
                        totalPages++
                    } catch (e: Exception) {
                        totalErrors++
                        AppLogger.w("CatalogIndexBuilder:${profile.name}", "Page $page failed: ${e.message}")
                        hasMore = false
                    }
                    page++
                }

                if (pageItems.isNotEmpty()) {
                    pageItems.chunked(100).forEach { chunk ->
                        catalogDao.insertAll(chunk)
                    }
                    totalItems += pageItems.size
                }
            }

            CatalogBuildResult(profile.name, totalItems, totalPages, totalErrors)
        }

    private fun buildPageUrl(baseUrl: String, path: String, page: Int): String {
        val cleanBase = baseUrl.trimEnd('/')
        return if (page <= 1) {
            "$cleanBase/$path"
        } else {
            "$cleanBase/$path/page/$page/"
        }
    }

    internal fun parseCategoryPage(
        html: String,
        baseUrl: String,
        contentType: String,
        profile: DleProviderProfile
    ): List<CatalogIndexEntity> {
        if (html.isBlank()) return emptyList()
        return try {
            val doc = Jsoup.parse(html, baseUrl)
            val items = doc.select(profile.selectors.cardItem)
            if (items.isEmpty()) return emptyList()

            val host = baseUrl.substringAfter("://").substringBefore("/")
            val results = mutableListOf<CatalogIndexEntity>()

            for (el in items) {
                try {
                    val linkEl = el.selectFirst(profile.selectors.cardLink) ?: continue
                    val url = linkEl.attr("abs:href")
                    if (url.isBlank() || !url.contains(host, ignoreCase = true)) continue

                    val titleEl = el.selectFirst(profile.selectors.cardTitle)
                    var title = titleEl?.text()?.trim()
                        ?: linkEl.attr("title").ifEmpty { linkEl.text().trim() }
                    if (title.isBlank()) continue
                    title = ContentUtils.cleanTitle(title)

                    val posterEl = el.selectFirst(profile.selectors.cardPoster)
                    val poster = posterEl?.let { p ->
                        p.attr("abs:data-src").ifEmpty { p.attr("abs:src") }
                    } ?: ""

                    val rating = extractRating(el, profile)
                    val quality = extractQuality(el, profile)
                    val (year, titleEn) = extractYearAndEnTitle(el, profile)

                    results.add(
                        CatalogIndexEntity(
                            url = url,
                            title = title,
                            titleEn = titleEn,
                            poster = poster,
                            provider = profile.name,
                            year = year,
                            rating = rating,
                            quality = quality,
                            contentType = contentType
                        )
                    )
                } catch (_: Exception) { }
            }

            results.distinctBy { it.url }
        } catch (e: Exception) {
            AppLogger.w("CatalogIndexBuilder:${profile.name}", "parse failed: ${e.message}")
            emptyList()
        }
    }

    private fun extractRating(el: org.jsoup.nodes.Element, profile: DleProviderProfile): String {
        return when (profile.name) {
            "Uakino" -> {
                val deck = el.select(".deck-value").firstOrNull()?.text() ?: ""
                if (deck.isNotBlank() && deck.any { it == '.' }) deck else ""
            }
            "Eneyida" -> {
                el.select(".ratingplus").firstOrNull()?.text() ?: ""
            }
            else -> ""
        }
    }

    private fun extractQuality(el: org.jsoup.nodes.Element, profile: DleProviderProfile): String {
        return when (profile.name) {
            "Uakino" -> {
                el.select(".full-quality").firstOrNull()?.text() ?: ""
            }
            "Eneyida" -> {
                el.select(".label_quel-hd").firstOrNull()?.text() ?: ""
            }
            else -> ""
        }
    }

    private fun extractYearAndEnTitle(el: org.jsoup.nodes.Element, profile: DleProviderProfile): Pair<String, String> {
        return when (profile.name) {
            "Eneyida" -> {
                val subtitle = el.select(".short_subtitle").firstOrNull()?.text()?.trim() ?: return "" to ""
                val yearMatch = YEAR_PATTERN.find(subtitle)
                val year = yearMatch?.value ?: ""
                val enTitle = subtitle.replaceFirst(YEAR_PATTERN, "").trim('/').trim()
                year to enTitle
            }
            else -> "" to ""
        }
    }

    companion object {
        private val YEAR_PATTERN = Regex("""\b(19|20)\d{2}\b""")

        val UakinoSources = listOf(
            CatalogSource("filmy/", "movie"),
            CatalogSource("seriesss/", "series")
        )

        val EneyidaSources = listOf(
            CatalogSource("f/sort=rating/order=desc", "unknown")
        )
    }
}
