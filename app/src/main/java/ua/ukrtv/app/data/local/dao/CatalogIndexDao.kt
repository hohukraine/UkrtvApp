package ua.ukrtv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ua.ukrtv.app.data.local.entity.CatalogIndexEntity

@Dao
interface CatalogIndexDao {
    @Query("SELECT * FROM catalog_index WHERE title LIKE '%' || :query || '%' OR titleEn LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun search(query: String, limit: Int = 30): List<CatalogIndexEntity>

    @Query("SELECT * FROM catalog_index WHERE provider = :provider AND (title LIKE '%' || :query || '%' OR titleEn LIKE '%' || :query || '%') LIMIT :limit")
    suspend fun searchByProvider(provider: String, query: String, limit: Int = 30): List<CatalogIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CatalogIndexEntity>)

    @Query("SELECT COUNT(*) FROM catalog_index WHERE provider = :provider")
    suspend fun countByProvider(provider: String): Int

    @Query("DELETE FROM catalog_index WHERE provider = :provider")
    suspend fun deleteByProvider(provider: String)

    @Query("DELETE FROM catalog_index")
    suspend fun deleteAll()

    @Query("SELECT url FROM catalog_index")
    suspend fun getAllUrls(): List<String>
}
