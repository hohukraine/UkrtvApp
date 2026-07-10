package ua.ukrtv.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalog_index",
    indices = [
        Index("title"),
        Index("titleEn"),
        Index("provider")
    ]
)
data class CatalogIndexEntity(
    @PrimaryKey val url: String,
    val title: String,
    val titleEn: String = "",
    val poster: String = "",
    val provider: String,
    val year: String = "",
    val rating: String = "",
    val quality: String = "",
    val contentType: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
