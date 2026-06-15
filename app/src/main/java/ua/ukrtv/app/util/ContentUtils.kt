package ua.ukrtv.app.util

import ua.ukrtv.app.domain.model.ContentType

object ContentUtils {
    fun cleanTitle(title: String): String {
        var clean = title.replace("+", " ").replace("_", " ")
        
        // 1. Видаляємо ID постів та лічильники на початку (числа до 7 цифр, за якими йде пробіл або якість)
        clean = clean.replace(Regex("""^\s*\d{1,7}\s*"""), "")
        
        // 2. Видаляємо технічні мітки якості
        val techRegex = Regex("""\b(FHD|HD|SD|720p|1080p|2160p|4K|HDR|BD-Rip|BDRip|DVDRip|WEB-DL|WEBRip|Rip)\b""", RegexOption.IGNORE_CASE)
        clean = clean.replace(techRegex, "")
        
        // 3. Видаляємо +число
        clean = clean.replace(Regex("""\+\d+"""), "")
        
        return clean.replace("дивитися онлайн", "", ignoreCase = true)
            .replace("дивитися", "", ignoreCase = true)
            .replace("дивись", "", ignoreCase = true)
            .replace("українською мовою", "", ignoreCase = true)
            .replace("українською", "", ignoreCase = true)
            .replace("онлайн", "", ignoreCase = true)
            .replace("в хорошій якості", "", ignoreCase = true)
            .replace("безкоштовно", "", ignoreCase = true)
            .replace(Regex("""\(\d{4}\)"""), "") 
            .replace(Regex("""[\[(]?\d+\s*(сезон|серія|серії).*?[\)\]]?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^Серіал\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^Мультсеріал\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^Фільм\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^Мультфільм\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun inferContentType(url: String, title: String = ""): ContentType {
        val lowerUrl = url.lowercase()
        val lowerTitle = title.lowercase()
        val isSeries = lowerUrl.contains("/seriesss/") ||
            lowerUrl.contains("/seriali/") ||
            lowerUrl.contains("/serialy/") ||
            lowerUrl.contains("/series/") ||
            lowerUrl.contains("/animeukr/") ||
            lowerUrl.contains("/anime/") ||
            lowerUrl.contains("/anime-serials/") ||
            lowerUrl.contains("/cartoon-series/") ||
            lowerUrl.contains("/multfilm-serials/") ||
            lowerUrl.contains("/cartoons/") ||
            lowerUrl.contains("/cartoon/") ||
            lowerUrl.contains("/cartoon/cartoonseries/") ||
            lowerTitle.contains("сезон") ||
            lowerTitle.contains("серіал") ||
            lowerTitle.contains("мультсеріал") ||
            lowerTitle.contains("серія") ||
            lowerTitle.contains("серії") ||
            lowerTitle.contains("тв-") ||
            lowerTitle.contains("season") ||
            lowerTitle.contains("episode")
            
        return if (isSeries) ContentType.SERIES else ContentType.MOVIE
    }
}
