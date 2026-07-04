package ua.ukrtv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ua.ukrtv.app.data.local.entity.WatchlistEntity

@Dao
interface WatchlistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE contentId = :contentId")
    suspend fun delete(contentId: String)

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAllWatchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT contentId FROM watchlist")
    suspend fun getAllWatchlistIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE contentId = :contentId)")
    suspend fun isInWatchlist(contentId: String): Boolean
}
