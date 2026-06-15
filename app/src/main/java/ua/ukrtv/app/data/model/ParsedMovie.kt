package ua.ukrtv.app.data.model

import ua.ukrtv.app.domain.model.ContentType

data class ParsedMovie(
    val id: String,
    val title: String,
    val poster: String,
    val posterAlt: String?,
    val pageUrl: String,
    val type: ContentType,
    val year: String?,
    val genres: List<String>,
    val shortDescription: String?
)
