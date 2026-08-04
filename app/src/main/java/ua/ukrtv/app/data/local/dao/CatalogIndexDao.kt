package ua.ukrtv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ua.ukrtv.app.data.local.entity.CatalogIndexEntity

@Dao
interface CatalogIndexDao {
    @Query("""
        SELECT * FROM catalog_index 
        WHERE title LIKE '%' || :query || '%' OR titleEn LIKE '%' || :query || '%' 
        ORDER BY 
            CASE 
                WHEN title = :query OR titleEn = :query THEN 0
                WHEN title LIKE :query || '%' OR titleEn LIKE :query || '%' THEN 1
                ELSE 2 
            END,
            CASE 
                WHEN contentType IN ('series', 'cartoon') THEN 0
                ELSE 1
            END
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 30): List<CatalogIndexEntity>

    @Query("""
        SELECT * FROM catalog_index 
        WHERE provider = :provider AND (title LIKE '%' || :query || '%' OR titleEn LIKE '%' || :query || '%') 
        ORDER BY 
            CASE 
                WHEN title = :query OR titleEn = :query THEN 0
                WHEN title LIKE :query || '%' OR titleEn LIKE :query || '%' THEN 1
                ELSE 2 
            END
        LIMIT :limit
    """)
    suspend fun searchByProvider(provider: String, query: String, limit: Int = 30): List<CatalogIndexEntity>

    @Query("""
        SELECT * FROM catalog_index 
        WHERE title LIKE '%' || :w1 || '%' AND title LIKE '%' || :w2 || '%' 
        ORDER BY 
            CASE 
                WHEN (title LIKE :w1 || '%' OR titleEn LIKE :w1 || '%') AND 
                     (title LIKE :w2 || '%' OR titleEn LIKE :w2 || '%') THEN 0
                ELSE 1 
            END
        LIMIT :limit
    """)
    suspend fun searchTwoWords(w1: String, w2: String, limit: Int = 30): List<CatalogIndexEntity>

    @Query("SELECT * FROM catalog_index WHERE title LIKE '%' || :w1 || '%' AND title LIKE '%' || :w2 || '%' AND title LIKE '%' || :w3 || '%' LIMIT :limit")
    suspend fun searchThreeWords(w1: String, w2: String, w3: String, limit: Int = 30): List<CatalogIndexEntity>

    @Query("SELECT * FROM catalog_index WHERE title LIKE '%' || :w1 || '%' AND title LIKE '%' || :w2 || '%' AND title LIKE '%' || :w3 || '%' AND title LIKE '%' || :w4 || '%' LIMIT :limit")
    suspend fun searchFourWords(w1: String, w2: String, w3: String, w4: String, limit: Int = 30): List<CatalogIndexEntity>

    @Query("SELECT * FROM catalog_index WHERE title LIKE '%' || :w1 || '%' AND title LIKE '%' || :w2 || '%' AND title LIKE '%' || :w3 || '%' AND title LIKE '%' || :w4 || '%' AND title LIKE '%' || :w5 || '%' LIMIT :limit")
    suspend fun searchFiveWords(w1: String, w2: String, w3: String, w4: String, w5: String, limit: Int = 30): List<CatalogIndexEntity>

    @Query("SELECT * FROM catalog_index WHERE title LIKE '%' || :w1 || '%' AND title LIKE '%' || :w2 || '%' AND title LIKE '%' || :w3 || '%' AND title LIKE '%' || :w4 || '%' AND title LIKE '%' || :w5 || '%' AND title LIKE '%' || :w6 || '%' LIMIT :limit")
    suspend fun searchSixWords(w1: String, w2: String, w3: String, w4: String, w5: String, w6: String, limit: Int = 30): List<CatalogIndexEntity>

    @Query("SELECT * FROM catalog_index WHERE rating != '' ORDER BY CAST(rating AS REAL) DESC LIMIT :limit")
    suspend fun getPopular(limit: Int = 24): List<CatalogIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CatalogIndexEntity>)

    @Query("SELECT COUNT(*) FROM catalog_index WHERE provider = :provider")
    suspend fun countByProvider(provider: String): Int

    @Query("DELETE FROM catalog_index WHERE provider = :provider")
    suspend fun deleteByProvider(provider: String)

    @Query("DELETE FROM catalog_index WHERE provider NOT IN (:providers)")
    suspend fun deleteByProviderNotIn(providers: List<String>)

    @Query("DELETE FROM catalog_index")
    suspend fun deleteAll()

    @Query("SELECT url FROM catalog_index")
    suspend fun getAllUrls(): List<String>

    @Query("SELECT url FROM catalog_index WHERE provider = :provider")
    suspend fun getUrlsByProvider(provider: String): List<String>
}
