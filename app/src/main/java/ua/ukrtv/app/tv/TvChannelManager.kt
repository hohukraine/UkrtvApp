package ua.ukrtv.app.tv

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ua.ukrtv.app.domain.model.Movie
import ua.ukrtv.app.domain.model.WatchProgress
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvChannelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TvChannelManager"
    }

    suspend fun publishWatchNextPrograms(progressList: List<WatchProgress>) {
        try {
            val channelId = getOrCreateChannel(
                "ua.ukrtv.channel.continue_watching",
                context.getString(ua.ukrtv.app.R.string.channel_continue_title),
                context.getString(ua.ukrtv.app.R.string.channel_continue_desc)
            )
            if (channelId < 0) return
            clearProgramsForChannel(channelId)

            progressList
                .filter { it.durationMs > 0 && it.progressPercentage < 95 }
                .take(10)
                .forEach { progress ->
                    val values = ContentValues().apply {
                        put(TvContractCompat.Programs.COLUMN_CHANNEL_ID, channelId)
                        put(TvContractCompat.Programs.COLUMN_TITLE, progress.title)
                        put("description",
                            context.getString(ua.ukrtv.app.R.string.progress_percent, progress.progressPercentage))
                        put(TvContractCompat.Programs.COLUMN_POSTER_ART_URI, progress.poster)
                        put("thumbnail_uri", progress.poster)
                        put("intent_uri", buildDeepLink(progress.contentId, progress.pageUrl))
                        put("internal_provider_id", progress.contentId)
                    }

                    try {
                        context.contentResolver.insert(TvContractCompat.Programs.CONTENT_URI, values)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to insert program: ${progress.title}", e)
                    }
                }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to publish WatchNext", e)
        }
    }

    suspend fun publishPopularPrograms(movies: List<Movie>) {
        try {
            val channelId = getOrCreateChannel(
                "ua.ukrtv.channel.popular",
                context.getString(ua.ukrtv.app.R.string.channel_popular_title),
                context.getString(ua.ukrtv.app.R.string.channel_popular_desc)
            )
            if (channelId < 0) return
            clearProgramsForChannel(channelId)

            movies.take(20).forEach { movie ->
                val values = ContentValues().apply {
                    put(TvContractCompat.Programs.COLUMN_CHANNEL_ID, channelId)
                    put(TvContractCompat.Programs.COLUMN_TITLE, movie.title)
                    put("description", movie.shortDescription ?: "")
                    put(TvContractCompat.Programs.COLUMN_POSTER_ART_URI, movie.poster)
                    put("thumbnail_uri", movie.poster)
                    put("intent_uri", buildDeepLink(movie.id, movie.pageUrl))
                    put("internal_provider_id", movie.id)
                }

                try {
                    context.contentResolver.insert(TvContractCompat.Programs.CONTENT_URI, values)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to insert program: ${movie.title}", e)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to publish popular programs", e)
        }
    }

    private fun getOrCreateChannel(internalId: String, displayName: String, description: String): Long {
        val existingId = findExistingChannel(internalId)
        if (existingId != null) return existingId

        val values = ContentValues().apply {
            put(TvContractCompat.Channels.COLUMN_TYPE, TvContractCompat.Channels.TYPE_PREVIEW)
            put(TvContractCompat.Channels.COLUMN_DISPLAY_NAME, displayName)
            put(TvContractCompat.Channels.COLUMN_DESCRIPTION, description)
            put(TvContractCompat.Channels.COLUMN_APP_LINK_INTENT_URI, "ua.ukrtv.app://home")
            put(TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID, internalId)
        }

        return try {
            val uri = context.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, values)
            val id = uri?.lastPathSegment?.toLongOrNull() ?: -1L
            if (id > 0) {
                TvContractCompat.requestChannelBrowsable(context, id)
            }
            id
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create channel: $displayName", e)
            -1L
        }
    }

    private fun findExistingChannel(internalId: String): Long? = try {
        val projection = arrayOf(TvContractCompat.Channels._ID)
        val selection = "${TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID} = ?"
        val selectionArgs = arrayOf(internalId)
        context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun clearProgramsForChannel(channelId: Long) {
        try {
            context.contentResolver.delete(
                TvContractCompat.Programs.CONTENT_URI,
                "${TvContractCompat.Programs.COLUMN_CHANNEL_ID} = $channelId",
                null
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to clear programs for channel $channelId", e)
        }
    }

    private fun buildDeepLink(contentId: String, pageUrl: String): String {
        return "ua.ukrtv.app://detail/${Uri.encode(contentId)}?url=${Uri.encode(pageUrl)}"
    }
}
