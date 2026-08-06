package ua.ukrtv.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "series_structure",
    indices = [Index("provider")]
)
data class SeriesStructureEntity(
    @PrimaryKey val url: String,
    val seasonsJson: String,
    val updatedAt: Long,
    val provider: String = ""
)
