package ua.ukrtv.app.util

import ua.ukrtv.app.domain.model.Movie
import java.text.Normalizer

object SearchScorer {

    private val TRANSLIT_MAP = mapOf(
        'а' to 'a', 'б' to 'b', 'в' to 'v', 'г' to 'h', 'ґ' to 'g',
        'д' to 'd', 'е' to 'e', 'є' to "ie", 'ж' to "zh", 'з' to 'z',
        'и' to 'y', 'і' to 'i', 'ї' to 'i', 'й' to 'i', 'к' to 'k',
        'л' to 'l', 'м' to 'm', 'н' to 'n', 'о' to 'o', 'п' to 'p',
        'р' to 'r', 'с' to 's', 'т' to 't', 'у' to 'u', 'ф' to 'f',
        'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ь' to "", 'ю' to "iu", 'я' to "ia",
        'ы' to 'y', 'э' to 'e', 'ё' to "io"
    )

    fun stripAccents(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}"), "")
    }

    fun normalizeTitle(text: String): String {
        return stripAccents(text).lowercase()
            .replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("[^a-zа-яіїєґ0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun transliterate(text: String): String {
        val sb = StringBuilder()
        for (ch in stripAccents(text).lowercase()) {
            sb.append(TRANSLIT_MAP[ch] ?: ch)
        }
        return sb.toString()
    }

    private fun normalizeForMatching(text: String): String {
        return transliterate(normalizeTitle(text))
    }

    fun tokenSetSimilarity(a: String, b: String): Float {
        val na = normalizeForMatching(a)
        val nb = normalizeForMatching(b)
        if (na.isEmpty() || nb.isEmpty()) return 0f
        val aTokens = na.split(" ").filter { it.isNotEmpty() }.toSet()
        val bTokens = nb.split(" ").filter { it.isNotEmpty() }.toSet()
        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0f
        val intersection = aTokens.intersect(bTokens).size.toFloat()
        val union = (aTokens + bTokens).size.toFloat()
        return intersection / union
    }

    fun bigramSimilarity(a: String, b: String): Float {
        val na = normalizeForMatching(a).replace(" ", "")
        val nb = normalizeForMatching(b).replace(" ", "")
        if (na.length < 2 || nb.length < 2) return 0f
        val aBigrams = (0 until na.length - 1).map { na.substring(it, it + 2) }.toSet()
        val bBigrams = (0 until nb.length - 1).map { nb.substring(it, it + 2) }.toSet()
        if (aBigrams.isEmpty() || bBigrams.isEmpty()) return 0f
        val intersection = aBigrams.intersect(bBigrams).size.toFloat()
        val total = (aBigrams.size + bBigrams.size).toFloat()
        if (total == 0f) return 0f
        // Dice coefficient: 2 * |intersection| / (|A| + |B|)
        // Better than Jaccard for substring/prefix matches
        return 2f * intersection / total
    }

    fun extractYear(text: String): Int? {
        return Regex("""\b(19\d{2}|20\d{2})\b""").find(text)?.value?.toIntOrNull()
    }

    private fun slugFromUrl(url: String): String {
        val trimmed = url.trimEnd('/')
        val last = trimmed.substringAfterLast('/')
        return normalizeTitle(last.removeSuffix(".html"))
    }

    private fun slugFromUrlLatin(url: String): String {
        return transliterate(slugFromUrl(url))
    }

    private fun isSeriesUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("/serials/") || lower.contains("/seriesss/") ||
            lower.contains("-sezon") || lower.contains("/serial/")
    }

    private fun buildQueryVariants(queries: List<String>): List<String> {
        val variants = mutableSetOf<String>()
        for (q in queries) {
            val nq = normalizeTitle(q)
            if (nq.isNotEmpty()) variants.add(nq)
            val tq = transliterate(q)
            if (tq.isNotEmpty()) variants.add(tq)
            // Handle я→ia transliteration → also try ya prefix for proper nouns
            if (tq.startsWith("ia") && tq.length > 2 && tq[2] in 'a'..'z') {
                variants.add("ya${tq.substring(2)}")
            }
        }
        return variants.toList()
    }

    fun pickBestMatch(
        results: List<Movie>,
        queries: List<String>,
        expectedYear: Int? = null
    ): Movie? {
        val queryVariants = buildQueryVariants(queries)
        if (queryVariants.isEmpty() || results.isEmpty()) return null

        var bestMovie: Movie? = null
        var bestScore = Float.NEGATIVE_INFINITY
        var bestConfidence = 0f

        for (movie in results) {
            val resultTitle = movie.title
            val resultSlugLatin = slugFromUrlLatin(movie.pageUrl)

            var maxMatch = 0f
            var maxConfidence = 0f

            for (qv in queryVariants) {
                val ts = tokenSetSimilarity(qv, resultTitle)
                val bg = bigramSimilarity(qv, resultTitle)
                val titleMatch = maxOf(ts, bg)
                val slugMatch = maxOf(
                    tokenSetSimilarity(qv, resultSlugLatin),
                    bigramSimilarity(qv, resultSlugLatin)
                )
                val match = maxOf(titleMatch, slugMatch * 0.85f)

                if (match > maxMatch) maxMatch = match
                val currentConfidence = maxOf(ts, bg)
                if (currentConfidence > maxConfidence) maxConfidence = currentConfidence
            }

            val resultYear = movie.year ?: extractYear(resultTitle) ?: extractYear(movie.pageUrl)
            val yearPoints = when {
                expectedYear != null && resultYear == expectedYear -> 0.3f
                expectedYear != null && resultYear != null -> -0.3f
                expectedYear != null -> -0.15f
                else -> 0f
            }

            val seriesPenalty = if (isSeriesUrl(movie.pageUrl)) -0.2f else 0f

            val score = maxMatch * 0.8f + yearPoints + seriesPenalty

            if (score > bestScore || (score == bestScore && maxConfidence > bestConfidence)) {
                bestScore = score
                bestConfidence = maxConfidence
                bestMovie = movie
            }
        }

        val bestResultYear = bestMovie?.let { it.year ?: extractYear(it.title) ?: extractYear(it.pageUrl) }
        val threshold = when {
            expectedYear != null && bestResultYear == expectedYear -> 0.3f
            expectedYear != null && bestResultYear == null -> 0.55f
            else -> 0.5f
        }

        if (bestMovie != null && bestConfidence >= threshold) return bestMovie

        if (bestMovie != null && bestConfidence >= 0.4f) return bestMovie

        return null
    }
}
