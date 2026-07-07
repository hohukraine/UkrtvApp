package ua.ukrtv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val id: String,
    val contentId: String,
    val episodeId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val title: String,
    val poster: String,
    val pageUrl: String,
    val timestamp: Long
)
