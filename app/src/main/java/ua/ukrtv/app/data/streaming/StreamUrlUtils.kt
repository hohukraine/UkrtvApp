package ua.ukrtv.app.data.streaming

import ua.ukrtv.app.domain.model.StreamType

val YOUTUBE_DOMAINS = listOf(
    "youtube.com", "youtu.be", "youtube-nocookie.com",
    "ytimg.com", "googlevideo.com", "yt.be"
)

val FORBIDDEN_PATTERNS = listOf(
    "youtube.com", "youtu.be", "youtube-nocookie.com",
    "trailer", "preview", "preview.", "embed",
    "трейлер", "прев'ю", "превью",
    "watch?v=", "shorts/"
)

fun isForbiddenUrl(url: String): Boolean = url.lowercase().let { l ->
    FORBIDDEN_PATTERNS.any { l.contains(it) } || YOUTUBE_DOMAINS.any { l.contains(it) }
}

fun isDirectStreamUrl(url: String): Boolean {
    val clean = url.lowercase().substringBefore("?").substringBefore("#")
    return clean.endsWith(".m3u8") || clean.endsWith(".mpd") || clean.endsWith(".mp4") || clean.endsWith(".webm")
}

fun isVodIdUrl(url: String): Boolean = url.lowercase().let { l ->
    (l.contains("/vod/") || l.startsWith("dleid://")) && !isDirectStreamUrl(url)
}

fun getStreamType(url: String): StreamType = when {
    url.substringBefore("?").substringBefore("#")
        .endsWith(".m3u8", ignoreCase = true) -> StreamType.HLS
    url.substringBefore("?").substringBefore("#")
        .endsWith(".mpd", ignoreCase = true) -> StreamType.MPD
    else -> StreamType.MP4
}

fun inferReferer(url: String): String = when {
    url.contains("eneyida.tv") || url.contains("hdvbua.pro") -> "https://eneyida.tv/"
    url.contains("uakino.best") || url.contains("ashdi.vip") -> "https://uakino.best/"
    else -> ""
}
