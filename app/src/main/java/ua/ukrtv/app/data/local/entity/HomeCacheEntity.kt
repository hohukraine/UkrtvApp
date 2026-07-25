package ua.ukrtv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_cache")
data class HomeCacheEntity(
    @PrimaryKey val providerName: String,
    val sectionsJson: String,
    val categoriesJson: String,
    val lastUpdated: Long,
    val categoryLastUpdated: Long
)
