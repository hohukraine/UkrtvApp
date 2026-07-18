package ua.ukrtv.app.player

data class ExternalPlayerInfo(
    val packageName: String,
    val label: String,
    val supportsHeaders: Boolean = false,
    val supportsSubtitles: Boolean = false,
    val supportsResume: Boolean = true,
    val supportsResult: Boolean = false
) {
    companion object {
        val VLC = ExternalPlayerInfo(
            packageName = "org.videolan.vlc",
            label = "VLC",
            supportsHeaders = false,
            supportsSubtitles = true,
            supportsResume = true,
            supportsResult = true
        )

        val MX_PLAYER_FREE = ExternalPlayerInfo(
            packageName = "com.mxtech.videoplayer.ad",
            label = "MX Player",
            supportsHeaders = true,
            supportsSubtitles = true,
            supportsResume = true,
            supportsResult = true
        )

        val MX_PLAYER_PRO = ExternalPlayerInfo(
            packageName = "com.mxtech.videoplayer.pro",
            label = "MX Player Pro",
            supportsHeaders = true,
            supportsSubtitles = true,
            supportsResume = true,
            supportsResult = true
        )

        val JUST_PLAYER = ExternalPlayerInfo(
            packageName = "com.brouken.player",
            label = "Just Player",
            supportsHeaders = true,
            supportsSubtitles = true,
            supportsResume = true,
            supportsResult = true
        )

        val KNOWN_PLAYERS = listOf(VLC, MX_PLAYER_FREE, MX_PLAYER_PRO, JUST_PLAYER)
    }
}
