package ua.ukrtv.app.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Stable
@Immutable
@Serializable
data class Movie(
    val id: String,
    val title: String,
    val poster: String,
    val pageUrl: String,
    val watchProgress: Int? = null,
    val rating: String? = null,
    val year: Int? = null,
    val quality: String? = null,
    val contentType: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val brandColor: String? = null
)

@Stable
@Immutable
@Serializable
data class HomeSection(
    val title: String,
    val items: List<Movie>
)
