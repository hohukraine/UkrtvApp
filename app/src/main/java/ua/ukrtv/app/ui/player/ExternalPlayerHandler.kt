package ua.ukrtv.app.ui.player

import android.content.Intent
import android.widget.Toast
import ua.ukrtv.app.player.ExternalPlayerInfo

fun PlayerViewModel.getCurrentExternalPlayerInfo(): ExternalPlayerInfo? {
    val packageName = playerPreferences.externalPlayerPackage.value
    return externalPlayerLauncher.getPlayerInfo(packageName)
}

fun PlayerViewModel.getInstalledExternalPlayers(): List<ExternalPlayerInfo> {
    return externalPlayerLauncher.detectInstalledPlayers()
}

fun PlayerViewModel.isExternalPlayerInstalled(): Boolean {
    val packageName = playerPreferences.externalPlayerPackage.value
    return externalPlayerLauncher.isInstalled(packageName)
}

fun PlayerViewModel.hasPendingExternalPlayerResult(): Boolean =
    savedStateHandle.get<Boolean>(PlayerViewModel.KEY_PENDING_RESULT) == true

fun PlayerViewModel.setExternalPlayerPackage(packageName: String) {
    playerPreferences.setExternalPlayerPackage(packageName)
}

suspend fun PlayerViewModel.openInExternalPlayer(): Boolean {
    val intent = createExternalPlayerIntent() ?: return false
    val ctx = appContext
    return try {
        ctx.startActivity(intent)
        true
    } catch (e: android.content.ActivityNotFoundException) {
        val playerInfo = getCurrentExternalPlayerInfo()
        android.widget.Toast.makeText(ctx, "Не знайдено зовнішній плеєр (${playerInfo?.label ?: ""})", android.widget.Toast.LENGTH_LONG).show()
        false
    }
}
