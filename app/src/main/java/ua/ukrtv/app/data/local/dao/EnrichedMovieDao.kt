package ua.ukrtv.app.data.local.dao

import androidx.room.*
import ua.ukrtv.app.data.local.entity.EnrichedMovieEntity

@Dao
interface EnrichedMovieDao {
    @Query("SELECT * FROM enriched_movies WHERE cacheKey = :key")
    suspend fun getByKey(key: String): EnrichedMovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movie: EnrichedMovieEntity)

    @Query("DELETE FROM enriched_movies WHERE timestamp < :threshold")
    suspend fun deleteOld(threshold: Long)
}
