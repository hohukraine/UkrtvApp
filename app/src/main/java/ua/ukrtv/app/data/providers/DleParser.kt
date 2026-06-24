package ua.ukrtv.app.data.providers

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import ua.ukrtv.app.domain.model.Comment
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail

class DleParser(private val profile: DleProviderProfile) {

    fun parseList(html: String): List<Movie> = parseList(Jsoup.parse(html, profile.baseUrl))

    fun parseList(element: Element): List<Movie> = element.select(profile.selectors.item).mapNotNull { el ->
        val link = el.selectFirst("a[href]") ?: return@mapNotNull null
        val url = link.attr("abs:href")
        
        val titleElement = el.selectFirst(profile.selectors.title)
        val title = titleElement?.selectFirst("b, h2")?.text()
            ?: titleElement?.text()
            ?: link.attr("title")
            ?: el.selectFirst("img")?.attr("alt")
            ?: link.text()

        if (title.isBlank()) return@mapNotNull null

        val rating = el.select(".rating-num, .imdb-rating, .kp-rating, .rate_res, .meta-item:contains(IMDB), .meta-item:contains(КП)")
            .firstOrNull()?.text()?.replace("IMDB:", "")?.replace("КП:", "")?.trim()?.take(4)

        Movie(
            id = url.hashCode().toString(),
            title = ContentUtils.cleanTitle(title),
            poster = extractPoster(el),
            year = Regex("""\b(19|20)\d{2}\b""").find(el.text())?.value,
            pageUrl = url,
            type = if (url.contains("-sezon") || el.text().contains("серія", true) || profile.seriesMarkers.any { url.contains(it) && !url.contains("/filmy/") }) ContentType.SERIES else ContentType.MOVIE,
            rating = rating
        )
    }

    private fun extractPoster(el: Element): String {
        val img = el.selectFirst(profile.selectors.poster) ?: el.selectFirst("img") ?: return ""
        return img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }.ifEmpty { img.attr("abs:data-original") }
    }

    fun parseDetail(html: String, url: String): MovieDetail = parseDetail(Jsoup.parse(html, url), url)

    fun parseDetail(doc: Document, url: String): MovieDetail {
        val title = doc.selectFirst("h1")?.text() ?: doc.title().substringBefore("|")
        
        fun extractInfo(vararg terms: String): List<String> {
            for (term in terms) {
                val element = doc.select("li:contains($term), .fi-item:contains($term), .movie-desk-item:contains($term), .meta-item:contains($term), .finfo li:contains($term)")
                    .firstOrNull() ?: continue

                val fiDesc = element.select(".fi-desc a").filter { it.text().isNotBlank() }
                if (fiDesc.isNotEmpty()) {
                    return fiDesc.map {
                        it.text().trim().removeSuffix(",")
                            .removePrefix(term).removePrefix(":").trim()
                    }.filter { it.isNotBlank() }
                }

                val value = element.select(".deck-value, span, a").filter { it.text().isNotBlank() }
                if (value.isNotEmpty()) {
                    return value.map {
                        it.text().trim().removeSuffix(",")
                            .removePrefix(term).removePrefix(":").trim()
                    }.filter { it.isNotBlank() }
                }

                val text = element.text().substringAfter(term).trim().removePrefix(":").trim()
                if (text.isNotBlank()) return text.split(",").map { it.trim() }
            }
            return emptyList()
        }

        val genres = extractInfo("Жанр", "Категорія")
        val country = extractInfo("Країна")
        val actors = extractInfo("Актори", "В ролях")
        val director = extractInfo("Режисер", "Списки")
        
        val year = doc.select("li:contains(Рік), .movie-desk-item:contains(Рік), .meta-item:contains(Рік)")
            .firstOrNull()?.text()?.let { t ->
                Regex("""\b(19|20)\d{2}\b""").find(t)?.value
            } ?: Regex("""\b(19|20)\d{2}\b""").find(doc.text())?.value

        var rating = doc.select(".rating-num, .imdb-rating, .kp-rating, .rate_res, .imdb b, .meta-item:contains(IMDB), ._rating, .full_rating")
            .firstOrNull()?.text()?.replace("IMDB:", "")?.trim()?.take(4)
        
        if (rating.isNullOrEmpty()) {
            rating = Regex("""IMDB:\s*(\d+(\.\d+)?)""").find(doc.text())?.groupValues?.get(1)
        }

        if (rating.isNullOrEmpty()) {
            rating = Regex("""(\d+\.\d+)\s*/\s*\d+""").find(doc.text())?.groupValues?.get(1)?.take(4)
        }

        val description = doc.select("#full-text, .full-text, .full-info .full-text, article div, .movie-description, .full-description, #full_content-desc, .full_content-desc")
            .asSequence()
            .filter { el -> 
                val text = el.text()
                val parents = el.parents()
                
                val isComment = parents.any { p -> 
                    val combined = (p.className() + " " + p.id()).lowercase()
                    combined.contains("comment") || 
                    combined.contains("comm-") || 
                    combined.contains("comm_") || 
                    combined.contains("feedback") ||
                    combined.contains("reply")
                }
                
                val isSidebarOrFooter = parents.any { p ->
                    val combined = (p.className() + " " + p.id()).lowercase()
                    combined.contains("side") || 
                    combined.contains("footer") || 
                    combined.contains("related") ||
                    combined.contains("header") ||
                    combined.contains("menu") ||
                    combined.contains("popular")
                }

                text.length > 80 && !isComment && !isSidebarOrFooter && el.select("a").size < 5
            }
            .maxByOrNull { it.text().length }
            ?.text() ?: ""

        val comments = parseComments(doc)

        return MovieDetail(
            id = url.hashCode().toString(),
            title = ContentUtils.cleanTitle(title),
            poster = if (profile.selectors.detailPoster != null) {
                doc.selectFirst(profile.selectors.detailPoster)?.attr("abs:src")
                    ?: doc.selectFirst(profile.selectors.detailPoster)?.attr("abs:data-src")
                    ?: doc.selectFirst("meta[property=og:image]")?.attr("abs:content")
                    ?: extractPoster(doc)
            } else {
                doc.selectFirst("meta[property=og:image]")?.attr("abs:content") ?: extractPoster(doc)
            },
            description = description,
            year = year,
            genres = genres,
            pageUrl = url,
            providerName = profile.name,
            seasons = null,
            streamUrl = null,
            contentType = if (
                (profile.seriesMarkers.any { url.contains(it) && !url.contains("/filmy/") }) ||
                url.contains("-sezon") ||
                (!url.contains("/filmy/") && doc.select(".fi-item:contains(сезон)").isNotEmpty())
            ) ContentType.SERIES else ContentType.MOVIE,
            rating = rating,
            country = country,
            actors = actors,
            director = director,
            comments = comments,
            brandColor = profile.brandColor
        )
    }

    fun parseComments(doc: org.jsoup.nodes.Document): List<Comment> {
        return doc.select(".comm-item").mapNotNull { el ->
            val author = el.selectFirst(".comm-author")?.text() ?: return@mapNotNull null
            val avatar = el.selectFirst(".comm-av img")?.attr("abs:src") ?: ""
            val text = el.selectFirst(".comm-body")?.text() ?: return@mapNotNull null
            val date = el.selectFirst(".comm-date")?.text() ?: ""
            Comment(author, avatar, text, date)
        }.take(15)
    }

    fun parseSearch(html: String): List<Movie> {
        val doc = Jsoup.parse(html, profile.baseUrl)
        val blacklist = listOf("топ", "добірка", "добырка", "кращі", "краші", "фільми 202", "серіали 202", "netflix", "imdb")
        
        val results = parseList(doc).filter { m ->
            val t = m.title.lowercase()
            blacklist.none { t.contains(it) }
        }.toMutableList()

        if (results.isEmpty()) {
            doc.select("a[href*='.html']").forEach { a ->
                val url = a.attr("abs:href")
                val title = a.attr("title").ifEmpty { a.text() }
                if (title.length > 3 && !url.contains("/?do=")) {
                    results.add(Movie(
                        id = url.hashCode().toString(),
                        title = ContentUtils.cleanTitle(title),
                        poster = "",
                        year = null,
                        pageUrl = url,
                        type = ContentType.MOVIE
                    ))
                }
            }
        }
        return results.distinctBy { it.pageUrl }
    }
}
