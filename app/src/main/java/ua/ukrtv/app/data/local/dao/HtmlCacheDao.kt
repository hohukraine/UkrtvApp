package ua.ukrtv.app.data.local.dao

import androidx.room.*
import ua.ukrtv.app.data.local.entity.HtmlCacheEntity

@Dao
interface HtmlCacheDao {
    @Query("SELECT * FROM html_cache WHERE url = :url")
    suspend fun getHtml(url: String): HtmlCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: HtmlCacheEntity)

    @Query("DELETE FROM html_cache WHERE timestamp < :threshold")
    suspend fun deleteOldCache(threshold: Long)

    @Query("DELETE FROM html_cache WHERE url = :url")
    suspend fun delete(url: String)
}
