package ua.ukrtv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tmdb_trends_cache")
data class TmdbTrendsCacheEntity(
    @PrimaryKey val provider: String,
    val moviesJson: String,
    val itemIdsJson: String,
    val cachedAt: Long
)
