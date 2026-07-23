package ua.ukrtv.app.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import ua.ukrtv.app.util.AppLogger
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerPool @Inject constructor(
    private val playerFactory: PlayerFactory
) {
    companion object {
        private const val MAX_POOL_SIZE = 3
    }

    private val pool = ConcurrentLinkedDeque<ExoPlayer>()

    fun acquire(
        context: Context,
        dsFactory: DataSource.Factory,
        thermalLevel: ThermalMonitor.QualityLevel = ThermalMonitor.QualityLevel.HIGH
    ): ExoPlayer {
        val recycled = pool.pollFirst()
        if (recycled != null) {
            resetPlayer(recycled)
            playerFactory.applyThermalToPlayer(recycled, thermalLevel)
            AppLogger.d("PlayerPool", "Reused player from pool (poolSize=${pool.size})")
            return recycled
        }
        AppLogger.d("PlayerPool", "Creating new player (pool empty)")
        return playerFactory.buildPlayer(context, dsFactory, thermalLevel)
    }

    private fun resetPlayer(player: ExoPlayer) {
        try {
            player.clearMediaItems()
            player.stop()
            player.clearVideoSurface()
            player.volume = 1f
            player.playWhenReady = false
            player.setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
        } catch (e: Exception) {
            AppLogger.w("PlayerPool", "Error resetting player: ${e.message}")
        }
    }

    fun release(player: ExoPlayer) {
        if (pool.size < MAX_POOL_SIZE) {
            pool.addLast(player)
            AppLogger.d("PlayerPool", "Returned player to pool (poolSize=${pool.size})")
        } else {
            AppLogger.d("PlayerPool", "Pool full, releasing player")
            player.release()
        }
    }

    fun prewarm(context: Context, dsFactory: DataSource.Factory) {
        val remaining = MAX_POOL_SIZE - pool.size
        if (remaining <= 0) return
        AppLogger.d("PlayerPool", "Prewarming $remaining player(s)")
        val mainHandler = Handler(Looper.getMainLooper())
        for (i in 0 until remaining) {
            mainHandler.post {
                try {
                    val player = playerFactory.buildPlayer(context, dsFactory)
                    pool.addLast(player)
                    AppLogger.d("PlayerPool", "Prewarmed player ${pool.size}/$MAX_POOL_SIZE")
                } catch (e: Exception) {
                    AppLogger.e("PlayerPool", "Failed to prewarm player", e)
                }
            }
        }
    }

    fun clear() {
        while (pool.isNotEmpty()) {
            try { pool.pollFirst()?.release() } catch (_: Exception) {}
        }
    }
}
