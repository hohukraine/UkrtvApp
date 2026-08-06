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
private val MAX_PAGE_RETRIES = 3
private val MAX_CONSECUTIVE_EMPTY = 5

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

private data class FetchResult(val html: String?, val permanent: Boolean)

private fun fetchPage(url: String): FetchResult {
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
            if (response.isSuccessful) {
                FetchResult(response.body?.string(), permanent = false)
            } else {
                val permanent = response.code in 400..499
                System.err.println("HTTP ${response.code} for $url" + if (permanent) " (permanent)" else "")
                FetchResult(null, permanent)
            }
        }
    } catch (e: Exception) {
        System.err.println("Failed to fetch $url: ${e.message}")
        FetchResult(null, permanent = false)
    }
}

private fun fetchPageWithRetry(url: String): FetchResult {
    var result = fetchPage(url)
    var attempt = 1
    while (result.html == null && !result.permanent && attempt < MAX_PAGE_RETRIES) {
        val delay = 2000L * attempt
        System.err.println("[$url] transient failure, retrying in ${delay}ms ($attempt/$MAX_PAGE_RETRIES)")
        Thread.sleep(delay)
        result = fetchPage(url)
        attempt++
    }
    return result
}

private fun buildPageUrl(baseUrl: String, path: String, page: Int): String {
    val cleanBase = baseUrl.trimEnd('/')
    val cleanPath = path.trim('/')
    return "$cleanBase/$cleanPath/page/$page/"
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

private fun parseUaflixItem(el: Element, baseUrl: String, contentType: String): CatalogItem? {
    try {
        val linkEl = el.selectFirst("a.vi-img") ?: return null
        val url = linkEl.attr("abs:href")
        if (url.isBlank() || !url.contains("uafix.net")) return null

        var title = linkEl.attr("title")
        if (title.isBlank()) {
            title = el.selectFirst("img")?.attr("alt") ?: ""
        }
        if (title.isBlank()) return null

        title = title.removePrefix("Смотреть ").removePrefix("Дивитися ").removeSuffix(" онлайн").trim()
        title = cleanTitle(title)

        val posterEl = el.selectFirst("img[data-src], img[src]")
        val poster = posterEl?.attr("abs:data-src")?.ifEmpty { posterEl.attr("abs:src") } ?: ""

        val age = el.selectFirst(".age")?.text() ?: ""
        // Year and rating are not on the card, but we can try to extract from title if needed
        // but for now let's keep them empty or find another way

        return CatalogItem(
            url = url, title = title, titleEn = "", poster = poster,
            provider = "UAFLIX", year = "", rating = age,
            quality = "", contentType = contentType
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
        "UAFLIX" -> ::parseUaflixItem
        else -> throw IllegalArgumentException("Unknown provider: $name")
    }
    val cardSelector = when (name) {
        "Uakino" -> ".movie-item, .short-item, .shortstory"
        "UAFLIX" -> ".video-item"
        else -> "article"
    }

    for ((path, contentType) in sources) {
        if (results.isNotEmpty()) {
            System.err.println("[$name] Waiting 5s before next category...")
            Thread.sleep(5000)
        }
        var page = 1
        var emptyPagesInRow = 0

        while (emptyPagesInRow < MAX_CONSECUTIVE_EMPTY && page <= MAX_PAGES) {
            val pageUrl = buildPageUrl(baseUrl, path, page)
            val fetch = fetchPageWithRetry(pageUrl)
            if (fetch.permanent) {
                System.err.println("[$name] $path: permanent HTTP error at page $page (end of category)")
                break
            }
            val html = fetch.html
            if (html.isNullOrBlank()) {
                emptyPagesInRow++
                page++
                continue
            }

            val doc = Jsoup.parse(html, baseUrl)
            val items = doc.select(cardSelector)

            if (items.isEmpty()) {
                emptyPagesInRow++
                page++
                continue
            }

            var pageCount = 0
            for (el in items) {
                val item = parser(el, baseUrl, contentType)
                if (item == null) {
                    continue
                }
                if (allItems.add(item.url)) {
                    results.add(item)
                    pageCount++
                }
            }

            if (pageCount == 0) {
                emptyPagesInRow++
                page++
                continue
            }

            emptyPagesInRow = 0

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
    var first = true
    for (item in items) {
        val inferredType = if (item.provider == "Uakino") {
            when {
                item.url.contains("/filmy/") -> "movie"
                item.url.contains("/seriesss/") -> "series"
                item.url.contains("/cartoon/") -> "cartoon"
                item.url.contains("/animeukr/") -> "series"
                item.url.contains("/news/") || item.url.contains("/anonsi/") || item.url.contains("/spilno-prodakshn/") -> null
                else -> item.contentType.takeIf { it != "unknown" } ?: "movie"
            }
        } else if (item.provider == "UAFLIX") {
            val slug = item.url.trimEnd('/').substringAfterLast("/")
            val isCategory = slug.contains("_") || 
                listOf("multseial", "documental", "pro_love", "school", "detective").any { slug.contains(it) }
            
            if (isCategory) null
            else when {
                item.url.contains("/film/") || item.url.contains("/films/") -> "movie"
                item.url.contains("/serials/") || item.url.contains("/anime/") || item.url.contains("/dorama/") -> "series"
                item.url.contains("/cartoons/") -> "cartoon"
                else -> item.contentType
            }
        } else item.contentType

        if (inferredType == null) continue

        if (!first) sb.append(",")
        first = false
        
        sb.append("""{"url":${jsonEscape(item.url)},"title":${jsonEscape(item.title)},"poster":${jsonEscape(item.poster)},"provider":${jsonEscape(item.provider)},"contentType":${jsonEscape(inferredType)},"updatedAt":${item.updatedAt}""")
        if (item.titleEn.isNotEmpty()) sb.append(""","titleEn":${jsonEscape(item.titleEn)}""")
        if (item.year.isNotEmpty()) sb.append(""","year":${jsonEscape(item.year)}""")
        if (item.rating.isNotEmpty()) sb.append(""","rating":${jsonEscape(item.rating)}""")
        if (item.quality.isNotEmpty()) sb.append(""","quality":${jsonEscape(item.quality)}""")
        sb.append("}")
    }
    sb.append("]")
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

    val uaflixItems = scrapeProvider("UAFLIX", "https://uafix.net/", listOf(
        "film/" to "movie",
        "serials/" to "series",
        "cartoons/" to "cartoon",
        "anime/" to "series",
        "dorama/" to "series"
    ))
    println("UAFLIX: ${uaflixItems.size} items")

    val allItems = uakinoItems + uaflixItems
    println("Total: ${allItems.size} items")

    val json = itemsToJson(allItems)
    File(outputPath).also { file ->
        file.parentFile?.mkdirs()
        file.writeText(json)
    }

    val elapsed = (System.currentTimeMillis() - startTime) / 1000
    println("Done in ${elapsed}s — ${json.length / 1024}KB written to $outputPath")
}
