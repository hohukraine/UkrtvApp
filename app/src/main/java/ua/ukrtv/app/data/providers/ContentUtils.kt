package ua.ukrtv.app.data.providers

import ua.ukrtv.app.domain.model.ContentType

object ContentUtils {
    
    private val YEAR_REGEX = Regex("""\(\d{4}\)""")
    private val TECH_REGEX = Regex("""\b(FHD|HD|SD|720p|1080p|2160p|4K|HDR|BD-Rip|BDRip|DVDRip|WEB-DL|WEBRip|Rip|CAMRip|TS|H264|HEVC)\b""", RegexOption.IGNORE_CASE)
    
    private val TECHNICAL_SUFFIX_REGEX = Regex("""(?:\s+\d+[-\s]*\d*)?\s*(?:сезон|серія|серії|серій|season|episode|sezon|seria|seriya|IMDB|голосів|рейтинг|rating|votes|переглядів|дивитися|онлайн).*$""", RegexOption.IGNORE_CASE)
    
    private val START_SERIES_PREFIX_REGEX = Regex("""^\d*[-\s]*\d*\s*(?:сезон|серія|серії|серій|season|episode|sezon|seria|seriya)\s*""", RegexOption.IGNORE_CASE)
    private val START_NUMERIC_PREFIX_REGEX = Regex("""^\d{1,8}\s+""")
    
    private val TRAILING_JUNK_REGEX = Regex("""\s+[воуіа]\b\s*$""", RegexOption.IGNORE_CASE)
    private val HTML_TAGS_REGEX = Regex("<[^>]*>")
    private val NON_ALPHANUM_REGEX = Regex("""[^\p{L}\d\s']""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val ROMAN_REGEX = Regex("""\b([IVX]|II|III|IV|V|VI|VII|VIII|IX|X)\b""", RegexOption.IGNORE_CASE)

    private val PARASITES = listOf(
        Regex("""\bдивитися онлайн\b""", RegexOption.IGNORE_CASE),
        Regex("""\bдивитися\b""", RegexOption.IGNORE_CASE),
        Regex("""\bдивись\b""", RegexOption.IGNORE_CASE),
        Regex("""\bонлайн\b""", RegexOption.IGNORE_CASE),
        Regex("""\bнаживо в\b""", RegexOption.IGNORE_CASE),
        Regex("""\bдивись наживо\b""", RegexOption.IGNORE_CASE),
        Regex("""\bонлайн в HD\b""", RegexOption.IGNORE_CASE),
        Regex("""\bонлайн в\b""", RegexOption.IGNORE_CASE),
        Regex("""\bукраїнською\b""", RegexOption.IGNORE_CASE)
    )

    private val ROMAN_MAP = mapOf("i" to "1", "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5", "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10")
    
    private val CYRILLIC_TO_LATIN = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'є' to "ie", 'ж' to "zh", 'з' to "z", 'и' to "y", 'і' to "i", 'ї' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ь' to "", 'ю' to "iu", 'я' to "ia"
    )

    fun cleanTitle(title: String): String {
        if (title.isBlank()) return ""
        
        var clean = if (title.contains(" / ")) title.substringAfterLast("/").trim() else title

        val stopMarkers = listOf("Жанр:", "Актори:", "Рік виходу:", "Короткий опис:", "0 IMDB:", " IMDB:")
        for (marker in stopMarkers) {
            val idx = clean.indexOf(marker, ignoreCase = true)
            if (idx != -1) clean = clean.substring(0, idx)
        }
        
        clean = clean.replace(TECHNICAL_SUFFIX_REGEX, "")
        clean = clean.replace(START_SERIES_PREFIX_REGEX, "")
        clean = clean.replace(START_NUMERIC_PREFIX_REGEX, "")
        
        clean = clean.replace("&amp;", "&").replace("&#039;", "'").replace("&rsquo;", "'")
            .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
            .replace(HTML_TAGS_REGEX, "").replace("+", " ").replace("_", " ")

        PARASITES.forEach { regex ->
            clean = clean.replace(regex, "")
        }
        
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
        return if (deduplicated.size > 8) deduplicated.take(6).joinToString(" ") else result
    }

    fun normalizeForMatch(text: String?): String {
        if (text == null) return ""
        var norm = text.lowercase().replace("'", "")
        val EN_TO_UKR_COMMON = mapOf("michael" to "майкл", "joker" to "джокер", "avatar" to "аватар")
        EN_TO_UKR_COMMON[norm]?.let { return it }
        
        norm = norm.map { ch -> CYRILLIC_TO_LATIN[ch] ?: ch.toString() }.joinToString("")
        norm = norm.replace(ROMAN_REGEX) { match -> ROMAN_MAP[match.value.lowercase()] ?: match.value }
        return norm.replace(Regex("""[^\p{L}\d]"""), "").trim()
    }

    fun isTitleMatch(providerTitle: String, tmdbTitle: String, strict: Boolean = false): Boolean {
        val pNorm = normalizeForMatch(cleanTitle(providerTitle))
        val tNorm = normalizeForMatch(cleanTitle(tmdbTitle))
        return if (strict) pNorm == tNorm else (pNorm == tNorm || (pNorm.contains(tNorm) && tNorm.length >= 3))
    }

    fun inferContentType(url: String, title: String = ""): ContentType {
        val l = (url + title).lowercase()
        return if (listOf("сезон", "серіал", "серія", "series", "/seriali/").any { l.contains(it) }) ContentType.SERIES else ContentType.MOVIE
    }

    fun extractTitleFromUrl(url: String): String {
        if (url.isBlank()) return ""
        return try {
            val slug = url.trimEnd('/').substringAfterLast('/').substringBeforeLast('.')
            val cleanSlug = if (slug.contains("-") && slug.substringBefore("-").all { it.isDigit() }) {
                slug.substringAfter("-")
            } else {
                slug
            }
            cleanSlug.replace("-", " ").replace("_", " ").trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun isPlayableStreamUrl(url: String): Boolean {
        val l = url.lowercase()
        if (l.endsWith(".html") || l.endsWith(".php") || l.endsWith(".htm")) return false
        if (l.contains("text/html") || l.contains("<html")) return false
        val isKnownMedia = l.contains(".m3u8") || l.contains(".mpd") || l.contains(".mp4") || l.contains("/hls/")
        return isKnownMedia
    }

    fun isDirect(url: String): Boolean {
        val l = url.lowercase()
        return l.contains(".m3u8") || l.contains(".mpd") || (l.contains(".mp4") && !l.contains("/vod/"))
    }
}
