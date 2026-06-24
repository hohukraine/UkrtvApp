package ua.ukrtv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_cache")
data class SearchCacheEntity(
    @PrimaryKey val query: String,
    val resultsJson: String, // JSON of List<Movie>
    val timestamp: Long = System.currentTimeMillis()
)
