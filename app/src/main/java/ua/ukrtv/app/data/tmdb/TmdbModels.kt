package ua.ukrtv.app.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbTrendingResponse(
    val page: Int = 0,
    val results: List<TmdbTrendingItem> = emptyList()
)

@Serializable
data class TmdbTrendingItem(
    val id: Long = 0,
    @SerialName("media_type") val mediaType: String = "",
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null
) {
    val displayTitle: String get() = title ?: name ?: originalTitle ?: originalName ?: ""
    val originalTitleValue: String get() = originalTitle ?: originalName ?: ""
    val year: Int? get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
}
