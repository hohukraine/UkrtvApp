package ua.ukrtv.app.data.local.dao

import androidx.room.*
import ua.ukrtv.app.data.local.entity.SearchHistoryEntity

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 10")
    suspend fun getRecent(): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: SearchHistoryEntity): Long

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun delete(query: String): Int
}
