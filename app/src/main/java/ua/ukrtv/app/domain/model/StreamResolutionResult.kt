package ua.ukrtv.app.domain.model

import androidx.compose.runtime.Stable
import kotlinx.parcelize.Parcelize
import android.os.Parcelable

@Stable
@Parcelize
data class StreamResolutionResult(
    val streamUrl: String,
    val streamType: StreamType,
    val referer: String,
    val fallbackStreams: List<String> = emptyList(),
    val providerName: String = "",
    val sourcePageUrl: String = "",
    val seasons: List<Season>? = null,
    val voiceover: String? = null
) : Parcelable

enum class StreamType {
    HLS, MPD, MP4, UNKNOWN
}
