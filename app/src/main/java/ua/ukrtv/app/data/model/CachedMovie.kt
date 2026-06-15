package ua.ukrtv.app.data.model

data class CachedMovie(
    val id: String,
    val title: String,
    val posterPath: String,
    val backdropPath: String,
    val overview: String,
    val year: String,
    val rating: Double,
    val providerUrl: String,
    val providerName: String,
    val cachedAt: Long,
    val watchProgress: Int = 0,
    val lastSeason: Int? = null,
    val lastEpisode: Int? = null,
    val genres: List<String> = emptyList()
)
