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

        val KNOWN_PLAYERS = listOf(VLC, JUST_PLAYER)
    }
}
