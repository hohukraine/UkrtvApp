package ua.ukrtv.app.ui.player

import android.content.Context
import android.content.Intent
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.player.ExternalPlayerLauncher
import ua.ukrtv.app.util.PlayerPreferences

class ExternalPlayerInteractor(
    context: Context,
    private val playerPreferences: PlayerPreferences
) {
    val launcher = ExternalPlayerLauncher(context)

    fun buildIntent(
        title: String,
        url: String,
        streamType: StreamType,
        referer: String,
        positionMs: Long,
        durationMs: Long
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
