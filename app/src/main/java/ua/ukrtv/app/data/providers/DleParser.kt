package ua.ukrtv.app.data.providers

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import ua.ukrtv.app.domain.model.Comment
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.util.PerformanceMonitor

private const val MIN_DESCRIPTION_LENGTH = 80
private const val MAX_COMMENTS = 10

class DleParser(private val profile: DleProviderProfile) {

    fun parseList(html: String): List<Movie> {
        val results = parseListFastJsoup(html, profile.baseUrl)
        if (results.isNotEmpty()) return results
        return parseListFastRegex(html, profile.baseUrl, isSearch = false)
    }

    fun parseDetail(html: String, url: String): MovieDetail = PerformanceMonitor.traceResult("DleParser.parseDetail") {
        val titleMatch = TITLE_REGEX.find(html)
        val title = titleMatch?.groupValues?.get(1)?.let {
            org.jsoup.parser.Parser.unescapeEntities(it.replace(Regex("<[^>]+>"), ""), false)
        } ?: ""

        val posterMatch = POSTER_REGEX.find(html)
        val poster = posterMatch?.groupValues?.get(1) ?: ""

        val yearMatch = YEAR_REGEX.find(html)
        val year = yearMatch?.value

        val doc = Jsoup.parse(html, url)
        val fullDetail = parseDetail(doc, url)

        return fullDetail.copy(
            title = if (title.isNotEmpty()) ContentUtils.cleanTitle(title) else fullDetail.title,
            poster = if (poster.isNotEmpty() && profile.selectors.detailPoster == null) DleResolutionUtils.ensureAbsoluteUrl(poster, url) else fullDetail.poster,
            year = year?.toIntOrNull() ?: fullDetail.year
        )
    }

    private fun extractDescription(container: Element): String {
        val candidates = listOf(
            "#full-text", ".full-text.clearfix", ".full-text[itemprop]",
            ".full-info .full-text", ".movie-description", ".full-description",
            "#full_content-desc", ".full_content-desc", ".story-description",
            ".full-story-text"
        ).mapNotNull { container.selectFirst(it) }

        var best = ""
        for (el in candidates) {
            val text = el.text()
            if (text.length <= MIN_DESCRIPTION_LENGTH) continue
            val cls = el.className() + " " + el.id()
            if (cls.contains("comment") || cls.contains("comm-")) continue
            if (text.contains("Додано S", true) && text.contains("E", true)) continue
            if (text.startsWith("/*")) continue
            if (el.closest(".side, .sidebar, .footer, #sidebar, #footer, .related, .sidebox") != null) continue
            if (text.length > best.length) best = text
        }
        return best
    }

    private fun extractValuesFromElement(el: Element, term: String): List<String> {
        val fiDesc = el.select(".fi-desc a")
        if (fiDesc.isNotEmpty()) {
            return fiDesc.map { it.text().trim().removeSuffix(",") }.filter { it.length > 1 }
        }

        val links = el.select("a")
        if (links.isNotEmpty()) {
            val linkTexts = links.map { it.text().trim().removeSuffix(",") }
                .filter { it.isNotBlank() && !it.contains(term, true) }
            if (linkTexts.isNotEmpty()) return linkTexts
        }
        
        val spans = el.select("span, .deck-value")
        if (spans.isNotEmpty()) {
            val spanTexts = spans.map { it.text().trim().removeSuffix(",") }
                .filter { it.isNotBlank() && !it.contains(term, true) }
            if (spanTexts.isNotEmpty()) return spanTexts
        }
        
        val text = el.text()
        val index = text.indexOf(term, ignoreCase = true)
        val extracted = if (index >= 0) {
            text.substring(index + term.length).trim().removePrefix(":").trim()
        } else ""

        return if (extracted.isNotBlank()) extracted.split(",").map { it.trim() } else emptyList()
    }

    fun parseDetail(doc: Document, url: String): MovieDetail {
        val container = profile.selectors.detailContainer?.let { doc.selectFirst(it) } ?: doc
        val title = doc.selectFirst("h1")?.text() ?: doc.title().substringBefore("|")

        val selectCache = mutableMapOf<String, org.jsoup.select.Elements>()
        fun sel(s: String) = selectCache.getOrPut(s) { container.select(s) }

        fun cachedExtractMetadata(field: String): List<String> {
            val rules = profile.metadataRules[field] ?: return emptyList()
            for (rule in rules.sortedByDescending { it.priority }) {
                val elements = sel(rule.selector)
                for (el in elements) {
                    val text = el.text()
                    if (rule.terms.isNotEmpty()) {
                        val match = rule.terms.find { term ->
                            text.contains(term, ignoreCase = true) ||
                            el.select(".fi-label").text().contains(term, true)
                        }
                        if (match != null) {
                            val vals = extractValuesFromElement(el, match)
                            if (vals.isNotEmpty()) return vals
                        }
                    } else {
                        val value = if (rule.attribute != null) el.attr(rule.attribute) else text
                        if (value.isNotBlank()) return listOf(value.trim())
                    }
                }
            }
            return emptyList()
        }

        val genres = cachedExtractMetadata("genres").filter { it.lowercase() !in profile.nonGenreLabels }
        val country = cachedExtractMetadata("country")
        val actors = cachedExtractMetadata("actors")
        val director = cachedExtractMetadata("director")
        val duration = cachedExtractMetadata("duration").firstOrNull()

        val seasonCountRaw = cachedExtractMetadata("seasonCount").firstOrNull() ?: ""
        val seasonCount = DIGIT_REGEX.find(seasonCountRaw)?.groupValues?.get(1)?.toIntOrNull()
        
        val description = extractDescription(container)
        val comments = parseComments(doc)
        
        val ratingValues = cachedExtractMetadata("rating")
        val jsoupRating = if (ratingValues.isNotEmpty()) {
            val raw = ratingValues.first()
            RATING_CLEAN_REGEX.find(raw)?.value ?: raw
        } else {
            IMDB_FULL_REGEX.find(container.text())?.groupValues?.get(1)
        }

        val yearStr = cachedExtractMetadata("year").firstOrNull()
        val year = yearStr?.let { DIGIT_REGEX.find(it)?.value?.toIntOrNull() }

        return MovieDetail(
            id = url,
            title = ContentUtils.cleanTitle(title),
            poster = profile.selectors.detailPoster
                ?.let { doc.selectFirst(it) }
                ?.let { el -> el.attr("abs:data-src").ifEmpty { el.attr("abs:src") } }
                .takeIf { !it.isNullOrBlank() }
                ?: doc.selectFirst("meta[property=og:image]")?.attr("abs:content") ?: "",
            description = description,
            year = year,
            genres = genres,
            pageUrl = url,
            providerName = profile.name,
            seasons = null,
            streamUrl = null,
            rating = jsoupRating, 
            country = country,
            actors = actors,
            director = director,
            duration = duration,
            seasonCount = seasonCount,
            comments = comments,
            brandColor = profile.brandColor
        )
    }

    fun parseComments(doc: Document): List<Comment> {
        val items = doc.select(".comm-item, .comment, .comments-tree-item, .bestcomment")
        if (items.isEmpty()) return emptyList()

        return items.mapNotNull { el ->
            val author = el.selectFirst(".comm-author, .author, .best-comm-av img, b")?.let {
                if (it.tagName() == "img") it.attr("alt") else it.text()
            } ?: return@mapNotNull null
            
            val text = el.selectFirst(".comm-two, .comm-body, .comm-text, .comment-text, .text")?.text() ?: return@mapNotNull null
            
            val avatar = el.selectFirst(".comm-av img, .comm-left img, .best-comm-av img, .avatar img")?.attr("abs:src") ?: ""
            
            val date = el.selectFirst(".comm-date, .comm-one span:nth-child(3), .comm-one, .comm-body .date")?.text() ?: ""
            
            Comment(author, avatar, text, date)
        }.distinctBy { it.text }.take(MAX_COMMENTS)
    }

    fun parseSearch(html: String): List<Movie> {
        val results = parseListFastJsoup(html, profile.baseUrl)
        return if (results.isNotEmpty()) {
            results.filterNot { movie ->
                val lowerTitle = movie.title.lowercase()
                SEARCH_BLACKLIST.any { lowerTitle.contains(it) }
            }
        } else {
            parseListFastRegex(html, profile.baseUrl, isSearch = true)
        }
    }

    fun parseListFastJsoup(html: String, baseUrl: String): List<Movie> = PerformanceMonitor.traceResult("DleParser.parseListFastJsoup") {
        val results = mutableListOf<Movie>()
        if (html.isEmpty()) return@traceResult results
        try {
            val doc = org.jsoup.Jsoup.parse(html, baseUrl)
            val host = baseUrl.substringAfter("://").substringBefore("/")
            val cards = doc.select(profile.selectors.cardItem)
            if (cards.isEmpty()) return@traceResult results

            for (el in cards) {
                val linkEl = el.selectFirst(profile.selectors.cardLink) ?: continue
                val url = linkEl.attr("abs:href")
                if (url.isBlank() || !url.contains(host, ignoreCase = true)) continue

                val titleEl = el.selectFirst(profile.selectors.cardTitle)
                var title = titleEl?.text()?.trim() ?: linkEl.attr("title").ifEmpty { linkEl.text().trim() }
                if (title.isBlank()) continue
                title = ContentUtils.cleanTitle(title)

                val posterEl = el.selectFirst(profile.selectors.cardPoster)
                val poster = posterEl?.let { p ->
                    val src = p.attr("abs:data-src").ifEmpty { p.attr("abs:src") }
                    if (src.isNotBlank()) src else ""
                } ?: ""

                val imdbText = el.select(".r_imdb span, .rating-imdb, .imdb-mark").firstOrNull()?.text()
                val rating = imdbText?.let { RATING_CLEAN_REGEX.find(it)?.value }
                    ?: el.select(".rating-num, .rating_imdb").firstOrNull()?.text()
                    ?: IMDB_FULL_REGEX.find(el.text())?.groupValues?.get(1)

                results.add(Movie(id = url, title = title, poster = poster, pageUrl = url, rating = rating))
            }
        } catch (_: Exception) { }
        results.distinctBy { it.pageUrl }
    }

    companion object {
        private val TITLE_REGEX = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.IGNORE_CASE)
        private val POSTER_REGEX = Regex("""<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val YEAR_REGEX = Regex("""\b(19|20)\d{2}\b""")
        private val RATING_CLEAN_REGEX = Regex("""\d+\.?\d*""")
        private val DIGIT_REGEX = Regex("""(\d+)""")
        private val IMDB_FULL_REGEX = Regex("""IMDB[:\s>]*([\d.]+)""", RegexOption.IGNORE_CASE)
        private val RATING_GENERIC_REGEX = Regex("""(?:rating[-_]num|rating[-_]imdb|рейтинг)[^>]*[:\s>]*([\d.]+)""", RegexOption.IGNORE_CASE)

        private val POSTER_PAT = java.util.regex.Pattern.compile("""(?:data[-_]src|src|data[-_]original)\s*=\s*["']([^"']+\.(?:webp|jpg|jpeg|png|gif)(?:\?[^"']*)?)["']""", java.util.regex.Pattern.CASE_INSENSITIVE)
        private val TITLE_ATTR_PAT = java.util.regex.Pattern.compile("""title\s*=\s*["']([^"']{2,})["']""", java.util.regex.Pattern.CASE_INSENSITIVE)
        private val TITLE_TEXT_PAT = java.util.regex.Pattern.compile("""<(?:a|span|div)[^>]*class="[^"]*(?:movie[-_]title|short[-_]title)[^"]*"[^>]*>\s*(?:<b>)?([^<]{2,}?)(?:</b>)?\s*</(?:a|span|div)>""", java.util.regex.Pattern.CASE_INSENSITIVE)
        private val URL_PAT = java.util.regex.Pattern.compile("""href\s*=\s*["']([^"']+\.html)["']""", java.util.regex.Pattern.CASE_INSENSITIVE)
        
        private val SEARCH_BLACKLIST = setOf("топ", "netflix", "добірка", "списки", "колекція", "фільми за роками", "підбірка")

        fun parseListFastJsoupStatic(html: String, baseUrl: String, selectors: DleProviderProfile.Selectors): List<Movie> {
            val results = mutableListOf<Movie>()
            if (html.isEmpty()) return results
            try {
                val doc = org.jsoup.Jsoup.parse(html, baseUrl)
                val host = baseUrl.substringAfter("://").substringBefore("/")
                val cards = doc.select(selectors.cardItem)
                if (cards.isEmpty()) return results

                for (el in cards) {
                    val linkEl = el.selectFirst(selectors.cardLink) ?: continue
                    val url = linkEl.attr("abs:href")
                    if (url.isBlank() || !url.contains(host, ignoreCase = true)) continue

                    val titleEl = el.selectFirst(selectors.cardTitle)
                    var title = titleEl?.text()?.trim() ?: linkEl.attr("title").ifEmpty { linkEl.text().trim() }
                    if (title.isBlank()) continue
                    title = ContentUtils.cleanTitle(title)

                    val posterEl = el.selectFirst(selectors.cardPoster)
                    val poster = posterEl?.let { p ->
                        val src = p.attr("abs:data-src").ifEmpty { p.attr("abs:src") }
                        if (src.isNotBlank()) src else ""
                    } ?: ""

                    val imdbText = el.select(".r_imdb span, .rating-imdb, .imdb-mark").firstOrNull()?.text()
                    val rating = imdbText?.let { RATING_CLEAN_REGEX.find(it)?.value }
                        ?: el.select(".rating-num, .rating_imdb").firstOrNull()?.text()
                        ?: IMDB_FULL_REGEX.find(el.text())?.groupValues?.get(1)

                    results.add(Movie(id = url, title = title, poster = poster, pageUrl = url, rating = rating))
                }
            } catch (_: Exception) { }
            return results.distinctBy { it.pageUrl }
        }

        fun parseListFastRegex(html: String, baseUrl: String, isSearch: Boolean = false): List<Movie> {
            val results = mutableListOf<Movie>()
            if (html.isEmpty()) return results
            
            val host = baseUrl.substringAfter("://").substringBefore("/")

            val matcher = URL_PAT.matcher(html)
            while (matcher.find()) {
                val rawUrl = matcher.group(1) ?: continue
                val url = DleResolutionUtils.ensureAbsoluteUrl(rawUrl, baseUrl)
                if (url.isBlank() || !url.contains(host, ignoreCase = true)) continue

                val blockStart = maxOf(0, matcher.start() - 500)
                val blockEnd = minOf(html.length, matcher.end() + 300)
                val block = html.substring(blockStart, blockEnd)

                val attrTm = TITLE_ATTR_PAT.matcher(block)
                var title = if (attrTm.find()) attrTm.group(1)?.trim() ?: "" else ""
                if (title.isBlank()) {
                    val textTm = TITLE_TEXT_PAT.matcher(block)
                    if (textTm.find()) title = textTm.group(1)?.trim() ?: ""
                }
                if (title.isBlank()) continue

                val cleanTitle = ContentUtils.cleanTitle(title)
                if (isSearch) {
                    val lowerTitle = cleanTitle.lowercase()
                    if (SEARCH_BLACKLIST.any { lowerTitle.contains(it) }) continue
                }

                val pm = POSTER_PAT.matcher(block)
                val poster = if (pm.find()) DleResolutionUtils.ensureAbsoluteUrl(pm.group(1) ?: "", baseUrl) else ""

                val imdbMatch = IMDB_FULL_REGEX.find(block)
                var rating = imdbMatch?.groupValues?.get(1)
                if (rating == null) {
                    val genericRatingMatch = RATING_GENERIC_REGEX.find(block)
                    rating = genericRatingMatch?.groupValues?.get(1)
                }

                results.add(Movie(id = url, title = cleanTitle, poster = poster, pageUrl = url, rating = rating))
            }
            return results.distinctBy { it.pageUrl }
        }
    }
}
