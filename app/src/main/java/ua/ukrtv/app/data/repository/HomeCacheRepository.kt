package ua.ukrtv.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ua.ukrtv.app.data.local.dao.HomeCacheDao
import ua.ukrtv.app.data.local.entity.HomeCacheEntity
import ua.ukrtv.app.domain.model.HomeSection
import ua.ukrtv.app.domain.model.Movie
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeCacheRepository @Inject constructor(
    private val homeCacheDao: HomeCacheDao,
    private val json: Json
) {
    suspend fun getHomeCache(providerName: String): List<HomeSection>? = withContext(Dispatchers.IO) {
        try {
            val entity = homeCacheDao.getCache(providerName) ?: return@withContext null
            if (entity.sectionsJson.isEmpty()) return@withContext null
            json.decodeFromString<List<HomeSection>>(entity.sectionsJson)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCacheTimestamp(providerName: String): Long {
        return homeCacheDao.getCache(providerName)?.lastUpdated ?: 0L
    }

    suspend fun saveHomeCache(providerName: String, sections: List<HomeSection>) = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(sections)
            val existing = homeCacheDao.getCache(providerName)
            if (existing != null) {
                homeCacheDao.updateSections(providerName, content, System.currentTimeMillis())
            } else {
                homeCacheDao.insertCache(HomeCacheEntity(
                    providerName = providerName,
                    sectionsJson = content,
                    categoriesJson = "",
                    lastUpdated = System.currentTimeMillis(),
                    categoryLastUpdated = 0L
                ))
            }
        } catch (_: Exception) { }
    }

    suspend fun saveEmptyCache(providerName: String) = withContext(Dispatchers.IO) {
        homeCacheDao.deleteCache(providerName)
    }

    suspend fun getCategoryCache(providerName: String): Map<String, List<Movie>>? = withContext(Dispatchers.IO) {
        try {
            val entity = homeCacheDao.getCache(providerName) ?: return@withContext null
            if (entity.categoriesJson.isEmpty()) return@withContext null
            json.decodeFromString<Map<String, List<Movie>>>(entity.categoriesJson)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveCategoryCache(providerName: String, categories: Map<String, List<Movie>>) = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(categories)
            val existing = homeCacheDao.getCache(providerName)
            if (existing != null) {
                homeCacheDao.updateCategories(providerName, content, System.currentTimeMillis())
            } else {
                homeCacheDao.insertCache(HomeCacheEntity(
                    providerName = providerName,
                    sectionsJson = "",
                    categoriesJson = content,
                    lastUpdated = 0L,
                    categoryLastUpdated = System.currentTimeMillis()
                ))
            }
        } catch (_: Exception) {}
    }

    suspend fun isCategoryCacheStale(providerName: String, staleHours: Long = 3): Boolean {
        val ts = homeCacheDao.getCache(providerName)?.categoryLastUpdated ?: 0L
        return (System.currentTimeMillis() - ts) / (60 * 60 * 1000L) >= staleHours
    }
}
