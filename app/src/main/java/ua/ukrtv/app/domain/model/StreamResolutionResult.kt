package ua.ukrtv.app.domain.model

import kotlinx.parcelize.Parcelize
import android.os.Parcelable
import ua.ukrtv.app.data.providers.MediaSource

@Parcelize
data class StreamResolutionResult(
    val streamUrl: String,
    val streamType: StreamType,
    val referer: String,
    val fallbackStreams: List<String> = emptyList(),
    val providerName: String = "",
    val sourcePageUrl: String = "",
    val source: MediaSource? = null
) : Parcelable

enum class StreamType {
    HLS, MPD, MP4, IFRAME, UNKNOWN
}
