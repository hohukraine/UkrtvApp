package ua.ukrtv.app.data.providers

import ua.ukrtv.app.data.TtlLruCache

object ContentUtils {

    private val titleCache = TtlLruCache<String, String>(200, 30 * 60 * 1000L)

    private val YEAR_REGEX = Regex("""\(\d{4}\)""")
    private val TECH_REGEX = Regex("""\b(FHD|HD|SD|720p|1080p|2160p|4K|HDR|BD-Rip|BDRip|DVDRip|WEB-DL|WEBRip|Rip|CAMRip|TS|H264|HEVC)\b""", RegexOption.IGNORE_CASE)

    private val TECHNICAL_SUFFIX_REGEX = Regex("""(?:\s+\d+[-\s]*\d*)?\s*(?:сезон|серія|серії|серій|season|episode|sezon|seria|seriya|IMDB|голосів|рейтинг|rating|votes|переглядів|дивитися|онлайн).*$""", RegexOption.IGNORE_CASE)

    private val START_SERIES_PREFIX_REGEX = Regex("""^\d*[-\s]*\d*\s*(?:сезон|серія|серії|серій|season|episode|sezon|seria|seriya)\s*""", RegexOption.IGNORE_CASE)

    private val TRAILING_JUNK_REGEX = Regex("""\s+[воуіа]\b\s*$""", RegexOption.IGNORE_CASE)
    private val HTML_TAGS_REGEX = Regex("<[^>]*>")
    private val NON_ALPHANUM_REGEX = Regex("""[^\p{L}\d\s']""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    private val PARASITES_REGEX = Regex(
        """\b(?:дивитися\s+онлайн|онлайн\s+в\s+HD|дивись\s+наживо|онлайн\s+в|наживо\s+в|дивитися|дивись|онлайн|українською)\b""",
        RegexOption.IGNORE_CASE
    )

    private val STOP_MARKERS = listOf("Жанр:", "Актори:", "Рік виходу:", "Короткий опис:", "0 IMDB:", " IMDB:")

    fun cleanTitle(title: String): String {
        if (title.isBlank()) return ""
        titleCache.get(title)?.let { return it }

        var clean = if (title.contains(" / ")) title.substringBefore(" / ").trim() else title

        for (marker in STOP_MARKERS) {
            val idx = clean.indexOf(marker, ignoreCase = true)
            if (idx != -1) clean = clean.substring(0, idx)
        }

        clean = clean.replace(TECHNICAL_SUFFIX_REGEX, "")
        clean = clean.replace(START_SERIES_PREFIX_REGEX, "")

        clean = org.jsoup.parser.Parser.unescapeEntities(clean, false)
            .replace(HTML_TAGS_REGEX, "").replace("+", " ").replace("_", " ")

        clean = clean.replace(PARASITES_REGEX, "")

        clean = clean.replace(TECH_REGEX, "")
        clean = clean.replace(YEAR_REGEX, "")

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

        val result = deduplicated.joinToString(" ")
        val final = if (deduplicated.size > 8) deduplicated.take(6).joinToString(" ") else result
        titleCache.put(title, final)
        return final
    }
}
