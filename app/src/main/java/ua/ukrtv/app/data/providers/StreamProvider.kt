package ua.ukrtv.app.data.providers

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import okhttp3.RequestBody
import ua.ukrtv.app.domain.model.ContentType
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.domain.model.StreamType

@Parcelize
data class SearchItem(
    val title: String,
    val url: String,
    val imageUrl: String,
    val provider: String,
    val type: ContentType = ContentType.MOVIE,
    val year: String? = null,
    val genres: List<String> = emptyList(),
    val shortDescription: String? = null
) : Parcelable

sealed class MediaSource : Parcelable {
    abstract val primaryUrl: String?
    abstract val referer: String
    abstract val providerName: String
    abstract val allUrls: List<String>

    @Parcelize
    data class Movie(
        val url: String,
        val fallbackUrls: List<String> = emptyList(),
        override val referer: String = "",
        override val providerName: String = "",
        val voiceover: String? = null,
        val subtitles: String? = null
    ) : MediaSource(), Parcelable {
        override val primaryUrl: String get() = url
        override val allUrls: List<String> get() = listOf(url) + fallbackUrls
    }

    @Parcelize
    data class Series(
        val seasons: List<ProviderSeason>,
        override val referer: String = "",
        override val providerName: String = ""
    ) : MediaSource(), Parcelable {
        override val primaryUrl: String? get() = seasons.firstOrNull()?.episodes?.firstOrNull()?.url
        override val allUrls: List<String> get() = seasons.flatMap { s -> s.episodes.map { it.url } }
    }
}

@Parcelize
data class ProviderSeason(
    val number: Int,
    val episodes: List<ProviderEpisode>
) : Parcelable

@Parcelize
data class ProviderEpisode(
    val number: Int,
    val title: String,
    val url: String,
    val voiceover: String? = null,
    val subtitles: String? = null
) : Parcelable

@Parcelize
data class StreamResult(
    val url: String,
    val source: MediaSource?,
    val providerName: String,
    val hostSource: String,
    val referer: String = "",
    val providerBaseUrl: String = ""
) : Parcelable

fun MediaSource.toStreamResolutionResult(
    providerName: String,
    sourcePageUrl: String
): StreamResolutionResult {
    val primary = when (this) {
        is MediaSource.Movie -> this.primaryUrl ?: ""
        is MediaSource.Series -> this.primaryUrl ?: ""
    }

    val streamType = when {
        primary.contains(".m3u8", ignoreCase = true) -> StreamType.HLS
        primary.contains(".mpd", ignoreCase = true) -> StreamType.MPD
        primary.contains(".mp4", ignoreCase = true) -> StreamType.MP4
        else -> StreamType.UNKNOWN
    }

    val fallback = when (this) {
        is MediaSource.Movie -> this.fallbackUrls
        is MediaSource.Series -> emptyList()
    }

    return StreamResolutionResult(
        streamUrl = primary,
        streamType = streamType,
        referer = referer,
        fallbackStreams = fallback,
        providerName = providerName,
        sourcePageUrl = sourcePageUrl,
        seasons = if (this is MediaSource.Series) this.seasons.map { it.toDomainSeason() } else null
    )

}

fun ProviderSeason.toDomainSeason(): Season = Season(
    number = this.number,
    episodes = this.episodes.map { ep ->
        Episode(
            id = ep.url,
            number = ep.number,
            title = ep.title,
            pageUrl = ep.url,
            voiceover = ep.voiceover,
            subtitles = ep.subtitles
        )
    }
)

fun SearchItem.toMovie(): Movie {
    return Movie(
        id = url.hashCode().toString(),
        title = title,
        poster = imageUrl,
        year = year,
        type = type,
        pageUrl = url,
        genres = genres,
        shortDescription = shortDescription
    )
}
