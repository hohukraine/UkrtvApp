package ua.ukrtv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ua.ukrtv.app.data.local.entity.TmdbTrendsCacheEntity

@Dao
interface TmdbTrendsCacheDao {
    @Query("SELECT * FROM tmdb_trends_cache WHERE provider = :provider")
    suspend fun get(provider: String): TmdbTrendsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: TmdbTrendsCacheEntity)

    @Query("DELETE FROM tmdb_trends_cache")
    suspend fun clearAll()

    @Query("DELETE FROM tmdb_trends_cache WHERE provider = :provider")
    suspend fun delete(provider: String)
}
