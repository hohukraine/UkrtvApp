package ua.ukrtv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "enriched_movies")
data class EnrichedMovieEntity(
    @PrimaryKey val cacheKey: String, // format: "enrich|title|year"
    val title: String,
    val originalTitle: String?,
    val poster: String,
    val shortDescription: String?,
    val year: String?,
    val type: String?, // Enum as string
    val timestamp: Long = System.currentTimeMillis()
)
