package ua.ukrtv.app.domain.model

data class Top200Movie(
    val rank: Int,
    val title: String,
    val originalTitle: String,
    val tmdbId: Int = 0,
    val comment: String,
    val year: String = "",
    val director: String = "",
    val genres: List<String> = emptyList(),
    val rating: Int = 0,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val accentColor: String = "#08121c",
    val onAccentColor: String = "#ffffff", // Колір тексту (білий або чорний для контрасту)
    val searchQueries: List<String> = emptyList()
)
