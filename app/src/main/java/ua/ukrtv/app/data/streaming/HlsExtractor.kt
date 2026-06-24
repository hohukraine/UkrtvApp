package ua.ukrtv.app.data.streaming

import android.util.Log
import ua.ukrtv.app.domain.model.StreamType

class HlsExtractor {

    data class ExtractResult(
        val url: String,
        val type: StreamType,
        val label: String? = null,
        val quality: Int = 0
    )

    private val hlsPattern = Regex("""(?:https?:)?//[^"'\s>]+?\.m3u8[^"'\s>]*""", RegexOption.IGNORE_CASE)
    private val mpdPattern = Regex("""(?:https?:)?//[^"'\s>]+?\.mpd[^"'\s>]*""", RegexOption.IGNORE_CASE)
    private val mp4Pattern = Regex("""(?:https?:)?//[^"'\s>]+?\.mp4[^"'\s>]*""", RegexOption.IGNORE_CASE)

    private fun normalizeUrl(url: String): String {
        return if (url.startsWith("//")) "https:$url" else url
    }

    fun extractFromHtml(html: String): List<ExtractResult> {
        val results = mutableListOf<ExtractResult>()
        
        hlsPattern.findAll(html).forEach { 
            results.add(ExtractResult(normalizeUrl(it.value), StreamType.HLS))
        }
        
        mpdPattern.findAll(html).forEach { 
            results.add(ExtractResult(normalizeUrl(it.value), StreamType.MPD))
        }

        mp4Pattern.findAll(html).forEach { 
            results.add(ExtractResult(normalizeUrl(it.value), StreamType.MP4))
        }

        val qualityPattern = Regex("""[\[({]?(\d{3,4}p?)[\])}]?\s*[:]?\s*((?:https?:)?//[^\s,"'\]]+)""", RegexOption.IGNORE_CASE)
        qualityPattern.findAll(html).forEach { match ->
            val label = match.groupValues[1]
            val url = normalizeUrl(match.groupValues[2])
            val type = when {
                url.contains(".m3u8", ignoreCase = true) -> StreamType.HLS
                url.contains(".mpd", ignoreCase = true) -> StreamType.MPD
                else -> StreamType.MP4
            }
            results.add(ExtractResult(url, type, label, label.filter { it.isDigit() }.toIntOrNull() ?: 0))
        }

        val labeledResults = results.map { result ->
            if (result.label == null) {
                val quality = Regex("""(\d{3,4})p?""").find(result.url)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (quality > 0) {
                    result.copy(label = "${quality}p", quality = quality)
                } else result
            } else result
        }

        val allResults = labeledResults.toMutableList()

        val base64Pattern = Regex("""["']([A-Za-z0-9+/]{40,})={0,2}["']""")
        base64Pattern.findAll(html).forEach { match ->
            try {
                val decoded = String(android.util.Base64.decode(match.groupValues[1], android.util.Base64.DEFAULT))
                if (decoded.contains(".m3u8") || decoded.contains(".mpd") || decoded.contains(".mp4")) {
                    allResults.addAll(extractFromHtml(decoded))
                }
            } catch (_: Exception) {}
        }

        return allResults.distinctBy { it.url }.sortedByDescending { it.quality }
    }
}
