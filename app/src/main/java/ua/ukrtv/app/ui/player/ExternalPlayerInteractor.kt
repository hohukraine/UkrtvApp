package ua.ukrtv.app.ui.player

import android.content.Context
import android.content.Intent
import ua.ukrtv.app.data.repository.WatchProgressRepository
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.player.ExternalPlayerLauncher
import ua.ukrtv.app.data.streaming.isDirectStreamUrl
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExternalPlayerInteractor(
    context: Context,
    private val playerPreferences: PlayerPreferences,
    private val watchProgressRepository: WatchProgressRepository
) {
    val launcher = ExternalPlayerLauncher(context)

    suspend fun buildIntent(
        contentId: String,
        title: String,
        url: String,
        streamType: StreamType,
        referer: String,
        positionMs: Long,
        durationMs: Long,
        season: Int?,
        episode: Int?,
        voiceover: String?,
        seasons: List<Season>
    ): Intent? {
        val packageName = playerPreferences.externalPlayerPackage.value
        val playerInfo = launcher.getPlayerInfo(packageName) ?: return null

        val config = ExternalPlayerLauncher.PlayerLaunchConfig(
            streamUrl = url,
            streamType = streamType,
            title = title,
            referer = referer,
            positionMs = positionMs,
            durationMs = durationMs
        )
        return launcher.buildIntent(playerInfo, config)
    }

    fun extractResult(resultCode: Int, data: Intent?) = launcher.extractResult(resultCode, data)
}
