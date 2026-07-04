package ua.ukrtv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val poster: String,
    val pageUrl: String,
    val contentType: String? = null,
    val rating: String? = null,
    val year: Int? = null,
    val addedAt: Long = System.currentTimeMillis()
)
