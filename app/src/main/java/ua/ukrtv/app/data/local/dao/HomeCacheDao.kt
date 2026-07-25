package ua.ukrtv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ua.ukrtv.app.data.local.entity.HomeCacheEntity

@Dao
interface HomeCacheDao {
    @Query("SELECT * FROM home_cache WHERE providerName = :providerName")
    suspend fun getCache(providerName: String): HomeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(entity: HomeCacheEntity)

    @Query("DELETE FROM home_cache WHERE providerName = :providerName")
    suspend fun deleteCache(providerName: String)

    @Query("UPDATE home_cache SET sectionsJson = :json, lastUpdated = :timestamp WHERE providerName = :providerName")
    suspend fun updateSections(providerName: String, json: String, timestamp: Long)

    @Query("UPDATE home_cache SET categoriesJson = :json, categoryLastUpdated = :timestamp WHERE providerName = :providerName")
    suspend fun updateCategories(providerName: String, json: String, timestamp: Long)
}
