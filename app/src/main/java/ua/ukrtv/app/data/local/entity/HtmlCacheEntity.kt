package ua.ukrtv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "html_cache")
data class HtmlCacheEntity(
    @PrimaryKey val url: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
