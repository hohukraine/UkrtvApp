package ua.ukrtv.app.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import ua.ukrtv.app.domain.model.Movie
import java.net.URLEncoder

class TvRecommendationManager(private val context: Context) {

    companion object {
        private const val WATCH_NEXT_TYPE_CONTINUE = 1
        private const val WATCH_NEXT_TYPE_NEXT = 2
    }

    private val watchNextUri: Uri = Uri.parse("content://${TvContractCompat.AUTHORITY}/watch_next_program")

    fun publishWatchNext(movie: Movie, progressPercent: Int = 0) {
        try {
            val encodedId = URLEncoder.encode(movie.id, "UTF-8")
            val encodedUrl = URLEncoder.encode(movie.pageUrl, "UTF-8")
            val deepLink = "ua.ukrtv.app://detail/$encodedId?url=$encodedUrl"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(deepLink)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val watchNextType = if (progressPercent > 0) WATCH_NEXT_TYPE_CONTINUE else WATCH_NEXT_TYPE_NEXT

            val program = WatchNextProgram.Builder()
                .setTitle(movie.title)
                .setPosterArtUri(Uri.parse(movie.poster))
                .setIntent(intent)
                .setWatchNextType(watchNextType)
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .build()

            context.contentResolver.insert(
                watchNextUri,
                program.toContentValues()
            )
        } catch (e: Exception) {
            ua.ukrtv.app.util.AppLogger.w("TvRecs", "Failed to publish watch next: ${e.message}")
        }
    }
}
