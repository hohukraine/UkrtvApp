package ua.ukrtv.app.data.providers

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.Voiceover

@Parcelize
data class SearchItem(
    val title: String,
    val url: String,
    val imageUrl: String,
    val provider: String,
    val type: String? = null,
    val year: String? = null
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
        override val providerName: String = ""
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

data class ProviderVoiceover(
    val name: String,
    val episodes: List<ProviderEpisode>
)

@Parcelize
data class ProviderSeason(
    val number: Int,
    val episodes: List<ProviderEpisode>,
    val voiceoverOptions: List<String> = emptyList()
) : Parcelable {
    val voiceovers: List<ProviderVoiceover> get() {
        val grouped = episodes.filter { it.voiceover != null }.groupBy { it.voiceover ?: "Default" }
        if (grouped.isNotEmpty()) {
            return grouped.map { (name, eps) ->
                ProviderVoiceover(name, eps.sortedBy { it.number })
            }
        }
        return listOf(ProviderVoiceover("Default", episodes.sortedBy { it.number }))
    }
}

@Parcelize
data class ProviderEpisode(
    val number: Int,
    val title: String,
    val url: String,
    val voiceover: String? = null,
    val poster: String = ""
) : Parcelable

fun ProviderSeason.toDomainSeason(poster: String = ""): Season = Season(
    number = this.number,
    voiceovers = this.voiceovers.map { v ->
        Voiceover(
            name = v.name,
            episodes = v.episodes.map { ep ->
                Episode(
                    number = ep.number,
                    title = ep.title,
                    url = ep.url,
                    poster = ep.poster.ifEmpty { poster }
                )
            }
        )
    }
)
