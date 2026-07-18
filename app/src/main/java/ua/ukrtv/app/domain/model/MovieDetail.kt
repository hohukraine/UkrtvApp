package ua.ukrtv.app.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.parcelize.Parcelize

@Immutable
@Stable
@Parcelize
data class Voiceover(
    val name: String,
    val episodes: List<Episode>
) : Parcelable

@Immutable
@Stable
@Parcelize
data class Season(
    val number: Int,
    val voiceovers: List<Voiceover>
) : Parcelable {
    val episodes: List<Episode> get() = voiceovers.firstOrNull()?.episodes ?: emptyList()
    val voiceoverOptions: List<String> get() = voiceovers.map { it.name }
}

@Immutable
@Stable
@Parcelize
data class Episode(
    val number: Int,
    val title: String,
    val url: String,
    val subtitles: String? = null,
    val poster: String = ""
) : Parcelable

@Stable
@Immutable
data class MovieDetail(
    val id: String,
    val title: String,
    val poster: String,
    val description: String,
    val year: Int?,
    val genres: List<String>,
    val pageUrl: String,
    val providerName: String,
    val seasons: List<Season>?,
    val streamUrl: String?,
    val rating: String? = null,
    val country: List<String> = emptyList(),
    val duration: String? = null,
    val actors: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val seasonCount: Int? = null,
    val comments: List<Comment> = emptyList(),
    val brandColor: String? = null
)
