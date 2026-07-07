package ua.ukrtv.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.ukrtv.app.data.local.dao.WatchProgressDao
import ua.ukrtv.app.data.local.entity.WatchProgressEntity
import ua.ukrtv.app.domain.model.WatchProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchProgressRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchProgressDao: WatchProgressDao
) {
    fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }

    private fun WatchProgressEntity.toDomain() = WatchProgress(
        contentId = contentId,
        episodeId = episodeId,
        positionMs = positionMs,
        durationMs = durationMs,
        title = title,
        poster = poster,
        pageUrl = pageUrl,
        timestamp = timestamp
    )

    suspend fun saveProgress(
        contentId: String,
        episodeId: String?,
        positionMs: Long,
        durationMs: Long,
        title: String = "",
        poster: String = "",
        pageUrl: String = ""
    ) {
        val id = if (episodeId != null) "${contentId}_$episodeId" else contentId
        val existing = watchProgressDao.getProgress(id)

        val updated = WatchProgressEntity(
            id = id,
            contentId = contentId,
            episodeId = episodeId,
            positionMs = positionMs,
            durationMs = durationMs,
            title = title.ifEmpty { existing?.title ?: "" },
            poster = poster.ifEmpty { existing?.poster ?: "" },
            pageUrl = pageUrl.ifEmpty { existing?.pageUrl ?: "" },
            timestamp = System.currentTimeMillis()
        )
        watchProgressDao.insert(updated)
    }

    suspend fun getProgress(contentId: String, episodeId: String? = null): WatchProgress? {
        val id = if (episodeId != null) "${contentId}_$episodeId" else contentId
        return watchProgressDao.getProgress(id)?.toDomain()
    }

    suspend fun deleteProgress(contentId: String) {
        watchProgressDao.deleteByContentId(contentId)
    }

    fun getAllProgress(): Flow<List<WatchProgress>> {
        return watchProgressDao.getAllProgress().map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
