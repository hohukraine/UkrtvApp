package ua.ukrtv.app.data.streaming

import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.domain.model.StreamType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HlsExtractor @Inject constructor() {

    data class ExtractResult(
        val url: String,
        val type: StreamType,
        val label: String? = null,
        val quality: Int = 0
    )

    fun extractFromHtml(html: String): List<ExtractResult> {
        val results = mutableListOf<ExtractResult>()
        
        HLS_PATTERN.findAll(html).forEach { 
            results.add(ExtractResult(normalizeUrl(it.value), StreamType.HLS))
        }

        // Catch encoded/escaped URLs (like \/)
        val escapedHtml = html.replace("\\/", "/")
        HLS_PATTERN.findAll(escapedHtml).forEach {
            results.add(ExtractResult(normalizeUrl(it.value), StreamType.HLS))
        }
        
        MPD_PATTERN.findAll(html).forEach { 
            results.add(ExtractResult(normalizeUrl(it.value), StreamType.MPD))
        }

        MP4_PATTERN.findAll(html).forEach { 
            results.add(ExtractResult(normalizeUrl(it.value), StreamType.MP4))
        }

        QUALITY_PATTERN.findAll(html).forEach { match ->
            val label = match.groupValues[1]
            val url = normalizeUrl(match.groupValues[2])
            val type = when {
                url.substringBefore("?").substringBefore("#").endsWith(".m3u8", ignoreCase = true) -> StreamType.HLS
                url.substringBefore("?").substringBefore("#").endsWith(".mpd", ignoreCase = true) -> StreamType.MPD
                else -> StreamType.MP4
            }
            results.add(ExtractResult(url, type, label, label.filter { it.isDigit() }.toIntOrNull() ?: 0))
        }

        val labeledResults = results.map { result ->
            if (result.label == null) {
                val quality = QUALITY_FINDER.find(result.url)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (quality > 0) {
                    result.copy(label = "${quality}p", quality = quality)
                } else result
            } else result
        }

        val allResults = labeledResults.toMutableList()

        BASE64_PATTERN.findAll(html).forEach { match ->
            val candidate = match.groupValues[1]
            if (candidate.length < 40 || candidate.length > 500) return@forEach
            try {
                val decoded = String(android.util.Base64.decode(candidate, android.util.Base64.DEFAULT))
                if (decoded.length in 20..2000 && (decoded.contains(".m3u8") || decoded.contains(".mpd") || decoded.contains(".mp4"))) {
                    allResults.addAll(extractFromHtml(decoded))
                }
            } catch (e: Exception) {
                AppLogger.d("HlsExtractor", "Failed to decode base64 candidate: ${e.message}")
            }
        }

        return allResults.sortedByDescending { it.quality }.distinctBy { it.url }
    }

    companion object {
        private val HLS_PATTERN = Regex("""(?:https?:)?//[^"'\s>]+?\.m3u8[^"'\s>]*""", RegexOption.IGNORE_CASE)
        private val MPD_PATTERN = Regex("""(?:https?:)?//[^"'\s>]+?\.mpd[^"'\s>]*""", RegexOption.IGNORE_CASE)
        private val MP4_PATTERN = Regex("""(?:https?:)?//[^"'\s>]+?\.mp4[^"'\s>]*""", RegexOption.IGNORE_CASE)
        private val QUALITY_PATTERN = Regex("""[\[({]?(\d{3,4}p?)[\])}]?\s*[:]?\s*((?:https?:)?//[^\s,"'\]]+)""", RegexOption.IGNORE_CASE)
        private val QUALITY_FINDER = Regex("""(\d{3,4})p?""")
        private val BASE64_PATTERN = Regex("""["']([A-Za-z0-9+/]{40,})={0,2}["']""")
    }

    private fun normalizeUrl(url: String): String {
        return if (url.startsWith("//")) "https:$url" else url
    }
}
