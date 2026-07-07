package ua.ukrtv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ua.ukrtv.app.data.local.entity.WatchProgressEntity

@Dao
interface WatchProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchProgressEntity)

    @Query("SELECT * FROM watch_progress WHERE id = :id")
    suspend fun getProgress(id: String): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress ORDER BY timestamp DESC")
    fun getAllProgress(): Flow<List<WatchProgressEntity>>

    @Query("DELETE FROM watch_progress WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM watch_progress WHERE contentId = :contentId")
    suspend fun deleteByContentId(contentId: String)
}
