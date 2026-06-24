package ua.ukrtv.app.data.local.dao

import androidx.room.*
import ua.ukrtv.app.data.local.entity.SearchCacheEntity

@Dao
interface SearchCacheDao {
    @Query("SELECT * FROM search_cache WHERE `query` = :query")
    suspend fun getResults(query: String): SearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: SearchCacheEntity): Long

    @Query("DELETE FROM search_cache WHERE timestamp < :threshold")
    suspend fun deleteOld(threshold: Long): Int
    
    @Query("DELETE FROM search_cache")
    suspend fun clearAll(): Int
}
