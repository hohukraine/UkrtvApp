package ua.ukrtv.app.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.Volatile

@UnstableApi
@Singleton
class PlayerWarmupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerFactory: PlayerFactory
) {
    @Volatile
    private var warmupPlayer: ExoPlayer? = null

    @Volatile
    private var warmupUrl: String? = null

    private val cleanupHandler = Handler(Looper.getMainLooper())
    private val cleanupRunnable = Runnable { release() }
    private val warmupTimeoutMs = 10_000L

    fun warmup(url: String, dataSourceFactory: DataSource.Factory) {
        release()

        val player = playerFactory.buildPlayer(context, dataSourceFactory)

        try {
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMimeType("application/x-mpegurl")
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            warmupPlayer = player
            warmupUrl = url
            AppLogger.d("Warmup", "Player warmup started for: ${url.take(40)}")
        } catch (e: Exception) {
            AppLogger.w("Warmup", "Warmup failed: ${e.message}")
            player.release()
            warmupPlayer = null
            warmupUrl = null
            return
        }

        cleanupHandler.removeCallbacks(cleanupRunnable)
        cleanupHandler.postDelayed(cleanupRunnable, warmupTimeoutMs)
    }

    fun takeAnyWarmupPlayer(): ExoPlayer? {
        cleanupHandler.removeCallbacks(cleanupRunnable)
        return warmupPlayer?.also {
            warmupPlayer = null
            warmupUrl = null
            AppLogger.d("Warmup", "Warmup player taken")
        }
    }

    fun takeWarmupPlayer(url: String): ExoPlayer? {
        cleanupHandler.removeCallbacks(cleanupRunnable)

        if (warmupPlayer != null && warmupUrl == url) {
            val player = warmupPlayer
            warmupPlayer = null
            warmupUrl = null
            AppLogger.d("Warmup", "Warmup player taken for: ${url.take(40)}")
            return player
        }

        if (warmupPlayer != null) {
            AppLogger.d("Warmup", "Warmup URL mismatch, releasing: ${warmupUrl?.take(40)} != ${url.take(40)}")
            release()
        }

        return null
    }

    fun release() {
        cleanupHandler.removeCallbacks(cleanupRunnable)
        warmupPlayer?.let { player ->
            try {
                player.stop()
                player.release()
            } catch (_: Exception) {}
        }
        warmupPlayer = null
        warmupUrl = null
    }
}
