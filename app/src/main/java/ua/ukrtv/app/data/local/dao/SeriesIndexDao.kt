package ua.ukrtv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ua.ukrtv.app.data.local.entity.SeriesIndexEntity

@Dao
interface SeriesIndexDao {
    @Query("SELECT * FROM series_index WHERE id = 0")
    suspend fun get(): SeriesIndexEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SeriesIndexEntity)

    @Query("DELETE FROM series_index")
    suspend fun delete()
}
