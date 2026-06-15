package ua.ukrtv.app.domain.model

enum class ContentType { MOVIE, SERIES }

data class Movie(
    val id: String,
    val title: String,
    val poster: String,
    val year: String?,
    val type: ContentType,
    val pageUrl: String,
    val genres: List<String> = emptyList(),
    val shortDescription: String? = null,
    val watchProgress: Int? = null
)

data class HomeSection(
    val title: String,
    val items: List<Movie>
)
