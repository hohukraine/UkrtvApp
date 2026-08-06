package ua.ukrtv.app.matching

import kotlinx.serialization.Serializable

@Serializable
data class TrendsSeedFile(
    val generatedAt: Long = 0,
    val providers: List<TrendsSeedProvider> = emptyList()
)

@Serializable
data class TrendsSeedProvider(
    val provider: String = "",
    val items: List<TrendsSeedItem> = emptyList()
)

@Serializable
data class TrendsSeedItem(
    val tmdbId: Long = 0,
    val url: String = "",
    val title: String = "",
    val poster: String = "",
    val provider: String = "",
    val year: String = "",
    val rating: String = "",
    val quality: String = "",
    val contentType: String = ""
)
