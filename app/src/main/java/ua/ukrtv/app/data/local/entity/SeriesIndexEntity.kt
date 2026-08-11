package ua.ukrtv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "series_index")
data class SeriesIndexEntity(
    @PrimaryKey val id: Int = 0,
    val indexJson: String,
    val updatedAt: Long
)
