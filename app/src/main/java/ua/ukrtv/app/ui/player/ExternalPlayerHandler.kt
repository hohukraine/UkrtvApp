package ua.ukrtv.app.ui.player

import ua.ukrtv.app.player.ExternalPlayerInfo

fun PlayerViewModel.getCurrentExternalPlayerInfo(): ExternalPlayerInfo? {
    val packageName = playerPreferences.externalPlayerPackage.value
    return externalPlayerInteractor.launcher.getPlayerInfo(packageName)
}

fun PlayerViewModel.getInstalledExternalPlayers(): List<ExternalPlayerInfo> {
    return externalPlayerInteractor.launcher.detectInstalledPlayers()
}

fun PlayerViewModel.isExternalPlayerInstalled(): Boolean {
    val packageName = playerPreferences.externalPlayerPackage.value
    return externalPlayerInteractor.launcher.isInstalled(packageName)
}

fun PlayerViewModel.hasPendingExternalPlayerResult(): Boolean =
    savedStateHandle.get<Boolean>(PlayerViewModel.KEY_PENDING_RESULT) == true

fun PlayerViewModel.setExternalPlayerPackage(packageName: String) {
    playerPreferences.setExternalPlayerPackage(packageName)
}
