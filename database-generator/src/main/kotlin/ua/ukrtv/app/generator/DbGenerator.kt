package ua.ukrtv.app.generator

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import java.util.concurrent.TimeUnit

data class CatalogItem(
    val url: String,
    val title: String,
    val titleEn: String,
    val poster: String,
    val provider: String,
    val year: String,
    val rating: String,
    val quality: String,
    val contentType: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ProviderConfig(
    val name: String,
    val baseUrl: String,
    val categoryPaths: List<Pair<String, String>>, // (path, contentType)
    val cardSelector: String,
    val titleSelector: String,
    val linkSelector: String = "a[href]",
    val posterAttr: String = "abs:data-src"
)

private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private val MAX_PAGES = 9999
private val TIMEOUT_MS = 15_000

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

private val YEAR_PATTERN = Regex("""\b(19|20)\d{2}\b""")
private val TECH_REGEX = Regex("""\b(FHD|HD|SD|720p|1080p|2160p|4K|HDR|BD-Rip|BDRip|DVDRip|WEB-DL|WEBRip|Rip|CAMRip|TS|H264|HEVC)\b""", RegexOption.IGNORE_CASE)
private val YEAR_BRACKET_REGEX = Regex("""\(\d{4}\)""")
private val TECHNICAL_SUFFIX_REGEX = Regex("""(?:\s+\d+[-\s]*\d*)?\s*(?:сезон|серія|серії|серій|season|episode|sezon|seria|seriya|IMDB|голосів|рейтинг|rating|votes|переглядів|дивитися|онлайн).*$""", RegexOption.IGNORE_CASE)
private val START_SERIES_PREFIX_REGEX = Regex("""^\d*[-\s]*\d*\s*(?:сезон|серія|серії|серій|season|episode|sezon|seria|seriya)\s*""", RegexOption.IGNORE_CASE)
private val START_NUMERIC_PREFIX_REGEX = Regex("""^\d{1,8}\s+""")
private val PARASITES_REGEX = Regex("""\b(?:дивитися\s+онлайн|онлайн\s+в\s+HD|дивись\s+наживо|онлайн\s+в|наживо\s+в|дивитися|дивись|онлайн|українською)\b""", RegexOption.IGNORE_CASE)
private val NON_ALPHANUM_REGEX = Regex("""[^\p{L}\d\s']""")
private val WHITESPACE_REGEX = Regex("""\s+""")
private val TRAILING_JUNK_REGEX = Regex("""\s+[воуіа]\b\s*$""", RegexOption.IGNORE_CASE)
private val HTML_TAGS_REGEX = Regex("<[^>]*>")

private fun cleanTitle(title: String): String {
    if (title.isBlank()) return ""
    var clean = if (title.contains(" / ")) title.substringBefore(" / ").trim() else title
    clean = clean.replace(TECHNICAL_SUFFIX_REGEX, "")
    clean = clean.replace(START_SERIES_PREFIX_REGEX, "")
    clean = clean.replace(START_NUMERIC_PREFIX_REGEX, "")
    clean = org.jsoup.parser.Parser.unescapeEntities(clean, false)
        .replace(HTML_TAGS_REGEX, "").replace("+", " ").replace("_", " ")
    clean = clean.replace(PARASITES_REGEX, "")
    clean = clean.replace(TECH_REGEX, "")
    clean = clean.replace(YEAR_BRACKET_REGEX, "")
    val finalClean = clean.replace(NON_ALPHANUM_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .replace(TRAILING_JUNK_REGEX, "")
        .trim()
    val words = finalClean.split(" ").filter { it.isNotEmpty() }
    val deduplicated = mutableListOf<String>()
    words.forEach { word ->
        if (deduplicated.isEmpty() || deduplicated.last().lowercase() != word.lowercase()) {
            deduplicated.add(word)
        }
    }
    return if (deduplicated.size > 8) deduplicated.take(6).joinToString(" ")
    else deduplicated.joinToString(" ")
}

private fun fetchPage(url: String): String? {
    return try {
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .header("Upgrade-Insecure-Requests", "1")
            .header("sec-ch-ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"Windows\"")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-User", "?1")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string()
            else {
                System.err.println("HTTP ${response.code} for $url")
                null
            }
        }
    } catch (e: Exception) {
        System.err.println("Failed to fetch $url: ${e.message}")
        null
    }
}

private fun buildPageUrl(baseUrl: String, path: String, page: Int): String {
    val cleanBase = baseUrl.trimEnd('/')
    return "$cleanBase/$path/page/$page/"
}

private fun parseUakinoItem(el: Element, baseUrl: String, contentType: String): CatalogItem? {
    try {
        val linkEl = el.selectFirst("a[href]") ?: return null
        val url = linkEl.attr("abs:href")
        if (url.isBlank() || !url.contains("uakino")) return null

        val titleEl = el.selectFirst(".movie-title, .short-title, .shortstory-title")
        var title = titleEl?.text()?.trim() ?: linkEl.attr("title").ifEmpty { linkEl.text().trim() }
        if (title.isBlank()) return null
        title = cleanTitle(title)

        val posterEl = el.selectFirst("img[data-src], img[src]")
        val poster = posterEl?.attr("abs:data-src")?.ifEmpty { posterEl.attr("abs:src") } ?: ""

        val ratingEl = el.selectFirst(".deck-value")
        val rating = ratingEl?.text()?.takeIf { it.any { c -> c == '.' } } ?: ""

        val quality = el.selectFirst(".full-quality")?.text() ?: ""

        return CatalogItem(
            url = url, title = title, titleEn = "", poster = poster,
            provider = "Uakino", year = "", rating = rating,
            quality = quality, contentType = contentType
        )
    } catch (_: Exception) { return null }
}

private var debugPage = false

private fun parseEneyidaItem(el: Element, baseUrl: String, contentType: String): CatalogItem? {
    try {
        val linkEl = el.selectFirst("a.short_title")
        if (linkEl == null) {
            if (debugPage) System.err.println("  PARSE FAIL: no a.short_title in article, class=${el.className()}")
            return null
        }
        val url = linkEl.attr("abs:href")
        if (url.isBlank() || !url.contains("eneyida")) {
            if (debugPage) System.err.println("  PARSE FAIL: url=$url, contains eneyida=${url.contains("eneyida")}")
            return null
        }

        val title = cleanTitle(linkEl.text().trim())
        if (title.isBlank()) return null

        val posterEl = el.selectFirst("img[data-src], img[src]")
        val poster = posterEl?.attr("abs:data-src")?.ifEmpty { posterEl.attr("abs:src") } ?: ""

        val subtitle = el.selectFirst(".short_subtitle")?.text()?.trim() ?: ""
        val yearMatch = YEAR_PATTERN.find(subtitle)
        val year = yearMatch?.value ?: ""
        val titleEn = subtitle.replaceFirst(YEAR_PATTERN, "").trim('/').trim()

        val rating = el.selectFirst(".ratingplus")?.text() ?: ""
        val quality = el.selectFirst(".label_quel-hd")?.text() ?: ""

        return CatalogItem(
            url = url, title = title, titleEn = titleEn, poster = poster,
            provider = "Eneyida", year = year, rating = rating,
            quality = quality, contentType = contentType
        )
    } catch (_: Exception) { return null }
}

private fun scrapeProvider(
    name: String,
    baseUrl: String,
    sources: List<Pair<String, String>>
): List<CatalogItem> {
    val allItems = mutableSetOf<String>()
    val results = mutableListOf<CatalogItem>()
    val parser: (Element, String, String) -> CatalogItem? = when (name) {
        "Uakino" -> ::parseUakinoItem
        "Eneyida" -> ::parseEneyidaItem
        else -> throw IllegalArgumentException("Unknown provider: $name")
    }
    val cardSelector = when (name) {
        "Uakino" -> ".movie-item, .short-item, .shortstory"
        "Eneyida" -> "article.short"
        else -> "article"
    }

    for ((path, contentType) in sources) {
        var page = 1
        var hasMore = true

        while (hasMore && page <= MAX_PAGES) {
            val pageUrl = buildPageUrl(baseUrl, path, page)
            val html = fetchPage(pageUrl) ?: break
            if (html.isBlank()) break

            val doc = Jsoup.parse(html, baseUrl)
            val items = doc.select(cardSelector)

            debugPage = false

            if (items.isEmpty()) {
                System.err.println("[$name] Page $page: 0 items matched selector (stopping)")
                break
            }

            var pageCount = 0
            for (el in items) {
                val item = parser(el, baseUrl, contentType)
                if (item == null) {
                    if (debugPage) {
                        val linkEl = el.selectFirst("a.short_title")
                        System.err.println("  PARSE NULL: class=${el.className()}, linkEl=${linkEl?.text()}, href=${linkEl?.attr("abs:href")}")
                    }
                    continue
                }
                if (allItems.add(item.url)) {
                    results.add(item)
                    pageCount++
                } else if (debugPage) {
                    System.err.println("  DUP: ${item.url}")
                }
            }

            if (pageCount == 0) {
                System.err.println("[$name] Page $page: 0 items after parsing (stopping)")
                break
            }

            if (page <= 3 || page % 10 == 0) {
                System.err.println("[$name] Page $page: +$pageCount items (total ${results.size})")
            }
            Thread.sleep(100) // avoid rate limiting

            page++
        }
        System.err.println("[$name] Done $path: ${results.size} total items")
    }

    return results
}

private fun itemsToJson(items: List<CatalogItem>): String {
    val sb = StringBuilder()
    sb.append("[")
    for ((i, item) in items.withIndex()) {
        if (i > 0) sb.append(",\n")
        sb.append("""
  { "url": ${jsonEscape(item.url)},
    "title": ${jsonEscape(item.title)},
    "titleEn": ${jsonEscape(item.titleEn)},
    "poster": ${jsonEscape(item.poster)},
    "provider": ${jsonEscape(item.provider)},
    "year": ${jsonEscape(item.year)},
    "rating": ${jsonEscape(item.rating)},
    "quality": ${jsonEscape(item.quality)},
    "contentType": ${jsonEscape(item.contentType)},
    "updatedAt": ${item.updatedAt} }""".trimIndent())
    }
    sb.append("\n]")
    return sb.toString()
}

private fun jsonEscape(s: String): String {
    val escaped = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}

fun main(args: Array<String>) {
    val outputPath = args.getOrNull(0) ?: "catalog_index.json"
    println("Catalog Index Generator")
    println("Output: $outputPath")
    println()

    val startTime = System.currentTimeMillis()

    val uakinoItems = scrapeProvider("Uakino", "https://uakino.best/", listOf(
        "find/year/" to "unknown"
    ))
    println("Uakino: ${uakinoItems.size} items")

    val eneyidaItems = scrapeProvider("Eneyida", "https://eneyida.tv/", listOf(
        "f/sort=new/order=desc" to "unknown"
    ))
    println("Eneyida: ${eneyidaItems.size} items")

    val allItems = uakinoItems + eneyidaItems
    println("Total: ${allItems.size} items")

    val json = itemsToJson(allItems)
    File(outputPath).also { file ->
        file.parentFile?.mkdirs()
        file.writeText(json)
    }

    val elapsed = (System.currentTimeMillis() - startTime) / 1000
    println("Done in ${elapsed}s — ${json.length / 1024}KB written to $outputPath")
}
