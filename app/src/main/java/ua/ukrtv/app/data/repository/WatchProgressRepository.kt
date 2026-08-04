package ua.ukrtv.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.ukrtv.app.Constants
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
        timestamp = timestamp,
        streamUrl = streamUrl,
        streamType = streamType,
        referer = referer
    )

    suspend fun saveProgress(data: ua.ukrtv.app.domain.model.ProgressData) {
        val id = if (data.episodeId != null) "${data.contentId}_${data.episodeId}" else data.contentId
        val existing = watchProgressDao.getProgress(id)

        val updated = WatchProgressEntity(
            id = id,
            contentId = data.contentId,
            episodeId = data.episodeId,
            positionMs = data.positionMs,
            durationMs = data.durationMs,
            title = data.title.ifEmpty { existing?.title ?: "" },
            poster = data.poster.ifEmpty { existing?.poster ?: "" },
            pageUrl = data.pageUrl.ifEmpty { existing?.pageUrl ?: "" },
            timestamp = System.currentTimeMillis(),
            streamUrl = data.streamUrl ?: existing?.streamUrl,
            streamType = data.streamType ?: existing?.streamType,
            referer = data.referer ?: existing?.referer,
            fallbackUrls = data.fallbackUrls ?: existing?.fallbackUrls,
            seasonsJson = data.seasonsJson ?: existing?.seasonsJson
        )
        watchProgressDao.insert(updated)
    }

    suspend fun saveProgress(
        contentId: String,
        episodeId: String?,
        positionMs: Long,
        durationMs: Long,
        title: String = "",
        poster: String = "",
        pageUrl: String = "",
        streamUrl: String? = null,
        streamType: String? = null,
        referer: String? = null,
        fallbackUrls: String? = null,
        seasonsJson: String? = null
    ) {
        saveProgress(ua.ukrtv.app.domain.model.ProgressData(
            contentId, episodeId, positionMs, durationMs, title, poster, pageUrl,
            streamUrl, streamType, referer, fallbackUrls, seasonsJson
        ))
    }

    suspend fun getProgress(contentId: String, episodeId: String? = null): WatchProgress? {
        val id = if (episodeId != null) "${contentId}_$episodeId" else contentId
        return watchProgressDao.getProgress(id)?.toDomain()
    }

    data class StreamCache(
        val streamUrl: String,
        val streamType: String,
        val referer: String,
        val fallbackUrls: List<String>,
        val durationMs: Long
    )

    suspend fun getStreamCache(contentId: String, episodeId: String?): StreamCache? {
        val id = if (episodeId != null) "${contentId}_$episodeId" else contentId
        val entity = watchProgressDao.getProgress(id) ?: return null
        if (entity.streamUrl == null || entity.streamType == null) return null
        if (System.currentTimeMillis() - entity.timestamp > Constants.STREAM_DB_CACHE_TTL_MS) {
            return null
        }
        return StreamCache(
            streamUrl = entity.streamUrl,
            streamType = entity.streamType,
            referer = entity.referer ?: "",
            fallbackUrls = entity.fallbackUrls?.split("|").orEmpty().filter { it.isNotEmpty() },
            durationMs = entity.durationMs
        )
    }

    suspend fun getStreamCacheForIds(ids: List<String>): Map<String, StreamCache> {
        val entities = watchProgressDao.getProgressForIds(ids)
        val now = System.currentTimeMillis()
        return entities.filter {
            it.streamUrl != null && it.streamType != null &&
            (now - it.timestamp <= Constants.STREAM_DB_CACHE_TTL_MS)
        }.associate { entity ->
            entity.id to StreamCache(
                streamUrl = entity.streamUrl!!,
                streamType = entity.streamType!!,
                referer = entity.referer ?: "",
                fallbackUrls = entity.fallbackUrls?.split("|").orEmpty().filter { it.isNotEmpty() },
                durationMs = entity.durationMs
            )
        }
    }

    suspend fun getSeasonsJson(contentId: String, episodeId: String?): String? {
        val id = if (episodeId != null) "${contentId}_$episodeId" else contentId
        val entity = watchProgressDao.getProgress(id) ?: return null
        return entity.seasonsJson
    }

    suspend fun cleanupOldEntries() {
        val threshold = System.currentTimeMillis() - Constants.DB_CLEANUP_THRESHOLD_MS
        watchProgressDao.deleteOlderThan(threshold)
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
