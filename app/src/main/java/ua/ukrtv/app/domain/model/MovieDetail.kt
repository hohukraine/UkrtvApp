package ua.ukrtv.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Season(
    val number: Int,
    val episodes: List<Episode>
) : Parcelable

@Parcelize
data class Episode(
    val id: String,
    val number: Int,
    val title: String,
    val pageUrl: String
) : Parcelable

data class MovieDetail(
    val id: String,
    val title: String,
    val poster: String,
    val description: String,
    val year: String?,
    val genres: List<String>,
    val pageUrl: String,
    val providerName: String,
    val seasons: List<Season>?,
    val streamUrl: String?,
    val contentType: ContentType = ContentType.MOVIE,
    val posterAlt: String? = null,
    val backdrop: String? = null,
    val originalTitle: String? = null,
    val rating: String? = null,
    val country: List<String> = emptyList(),
    val duration: String? = null,
    val actors: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val episodeCount: Int? = null,
    val seasonCount: Int? = null
) {
    val isSeries: Boolean get() = contentType == ContentType.SERIES || !seasons.isNullOrEmpty()
}
