package ua.ukrtv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ua.ukrtv.app.data.local.entity.SeriesStructureEntity

@Dao
interface SeriesStructureDao {
    @Query("SELECT * FROM series_structure WHERE url = :url")
    suspend fun get(url: String): SeriesStructureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SeriesStructureEntity)

    @Query("DELETE FROM series_structure WHERE updatedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    @Query("DELETE FROM series_structure")
    suspend fun deleteAll()
}
