package ua.ukrtv.app.domain.model

import androidx.compose.runtime.Immutable

enum class ContentType { MOVIE, SERIES }

@Immutable
data class Movie(
    val id: String,
    val title: String,
    val poster: String,
    val year: String?,
    val type: ContentType,
    val pageUrl: String,
    val genres: List<String> = emptyList(),
    val shortDescription: String? = null,
    val watchProgress: Int? = null,
    val originalTitle: String? = null,
    val rating: String? = null
)

@Immutable
data class HomeSection(
    val title: String,
    val items: List<Movie>
)

@Immutable
data class HomeData(
    val bannerMovie: Movie?,
    val sections: List<HomeSection>
)

