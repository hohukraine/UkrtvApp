package ua.ukrtv.app.player

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import ua.ukrtv.app.Constants
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.util.AppLogger

class ExternalPlayerLauncher(private val context: Context) {

    data class PlaylistItem(
        val url: String,
        val title: String,
        val streamType: StreamType = StreamType.MP4
    )

    data class PlayerLaunchConfig(
        val streamUrl: String,
        val streamType: StreamType,
        val title: String,
        val referer: String = "",
        val userAgent: String = Constants.USER_AGENT,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val subtitlesUrl: String? = null,
        val subtitlesName: String? = null,
        val playlist: List<PlaylistItem> = emptyList()
    )

    fun detectInstalledPlayers(): List<ExternalPlayerInfo> {
        return ExternalPlayerInfo.KNOWN_PLAYERS.filter { isInstalled(it.packageName) }
    }

    fun isInstalled(packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("http://test"), "video/*")
                setPackage(packageName)
            }
            intent.resolveActivity(context.packageManager) != null
        } catch (_: Exception) {
            false
        }
    }

    fun getPlayerInfo(packageName: String): ExternalPlayerInfo? {
        return ExternalPlayerInfo.KNOWN_PLAYERS.find { it.packageName == packageName }
            ?: run {
                val label = try {
                    val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                    context.packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    packageName.substringAfterLast('.')
                }
                ExternalPlayerInfo(
                    packageName = packageName,
                    label = label
                )
            }
    }

    fun buildIntent(playerInfo: ExternalPlayerInfo, config: PlayerLaunchConfig): Intent? {
        if (!isInstalled(playerInfo.packageName)) return null

        val intent = when (playerInfo.packageName) {
            "org.videolan.vlc" -> buildVlcIntent(config)
            "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.pro" -> buildMxPlayerIntent(playerInfo.packageName, config)
            "com.brouken.player" -> buildJustPlayerIntent(config)
            else -> buildGenericIntent(playerInfo.packageName, config)
        }

        AppLogger.d("ExternalPlayer", "buildIntent: package=${playerInfo.packageName} component=${intent.component} data=${intent.data} type=${intent.type} flags=0x${Integer.toHexString(intent.flags)}")

        if (intent.component != null) return intent

        return if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            intent.setDataAndType(intent.data, "video/*")
        }
    }

    private fun getMimeType(streamType: StreamType): String {
        return when (streamType) {
            StreamType.HLS -> "application/x-mpegURL"
            StreamType.MPD -> "application/dash+xml"
            StreamType.MP4 -> "video/mp4"
            else -> "video/*"
        }
    }

    private fun buildVlcIntent(config: PlayerLaunchConfig): Intent {
        val uri = Uri.parse(config.streamUrl)
        val mime = getMimeType(config.streamType)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            setPackage("org.videolan.vlc")
            setComponent(ComponentName("org.videolan.vlc", "org.videolan.vlc.gui.video.VideoPlayerActivity"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("title", config.title)
            putExtra("return_result", true)
            putExtra("extra_duration", config.durationMs)
            putExtra("from_start", config.positionMs <= 0L)
            if (config.positionMs > 0L) {
                putExtra("position", config.positionMs)
            }
            if (!config.subtitlesUrl.isNullOrBlank()) {
                putExtra("subtitles_location", config.subtitlesUrl)
            }
            if (config.playlist.isNotEmpty()) {
                val uris = config.playlist.map { Uri.parse(it.url) }.toTypedArray()
                val clipData = ClipData.newRawUri("Playlist", uris[0])
                for (i in 1 until uris.size) {
                    clipData.addItem(ClipData.Item(uris[i]))
                }
                setClipData(clipData)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun buildMxPlayerIntent(packageName: String, config: PlayerLaunchConfig): Intent {
        val uri = Uri.parse(config.streamUrl)
        val mime = getMimeType(config.streamType)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            setPackage(packageName)
            setClassName(packageName, "$packageName.ActivityScreen")
            putExtra("title", config.title)
            putExtra("return_result", true)
            if (config.positionMs > 0L) {
                putExtra("position", config.positionMs.toInt())
            }
            val headers = mutableListOf<String>()
            if (config.referer.isNotBlank()) {
                headers.add("Referer")
                headers.add(config.referer)
            }
            if (config.userAgent.isNotBlank()) {
                headers.add("User-Agent")
                headers.add(config.userAgent)
            }
            if (headers.isNotEmpty()) {
                putExtra("headers", headers.toTypedArray())
            }
            if (!config.subtitlesUrl.isNullOrBlank()) {
                putExtra("subs", arrayOf(Uri.parse(config.subtitlesUrl)))
                putExtra("subs.enable", arrayOf(Uri.parse(config.subtitlesUrl)))
                if (!config.subtitlesName.isNullOrBlank()) {
                    putExtra("subs.name", arrayOf(config.subtitlesName))
                }
            }

            if (config.playlist.isNotEmpty()) {
                val uris = config.playlist.map { Uri.parse(it.url) }.toTypedArray()
                val titles = config.playlist.map { it.title }.toTypedArray()
                putExtra("video_list", uris)
                putExtra("video_list.name", titles)
                putExtra("video_list_is_explicit", true)

                val clipData = ClipData.newRawUri("Playlist", uris[0])
                for (i in 1 until uris.size) {
                    clipData.addItem(ClipData.Item(uris[i]))
                }
                setClipData(clipData)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun buildJustPlayerIntent(config: PlayerLaunchConfig): Intent {
        val uri = Uri.parse(config.streamUrl)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            setPackage("com.brouken.player")
            setComponent(ComponentName("com.brouken.player", "com.brouken.player.PlayerActivity"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("title", config.title)
            putExtra("return_result", true)
            if (config.positionMs > 0L) {
                putExtra("position", config.positionMs.toInt())
            }
            val headers = mutableListOf<String>()
            if (config.referer.isNotBlank()) {
                headers.add("Referer")
                headers.add(config.referer)
            }
            if (config.userAgent.isNotBlank()) {
                headers.add("User-Agent")
                headers.add(config.userAgent)
            }
            if (headers.isNotEmpty()) {
                putExtra("headers", headers.toTypedArray())
            }
        }
    }

    private fun buildGenericIntent(packageName: String, config: PlayerLaunchConfig): Intent {
        val uri = Uri.parse(config.streamUrl)
        val mime = getMimeType(config.streamType)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("title", config.title)
            if (config.positionMs > 0L) {
                putExtra("position", config.positionMs)
            }
        }
    }

    fun extractResult(resultCode: Int, data: Intent?): ExternalPlayerResult? {
        if (resultCode != Activity.RESULT_OK) return null
        if (data == null) return null
        val extras = data.extras

        extras?.let { bundle ->
            val keys = bundle.keySet()
            val summary = keys.joinToString { key ->
                val v = bundle.get(key)
                "$key=$v"
            }
            AppLogger.d("ExternalPlayer", "Result extras (${keys.size}): $summary")
        } ?: AppLogger.d("ExternalPlayer", "Result extras: null")

        // VLC: extra_position / extra_duration (Long)
        var positionMs = extras?.getLong("extra_position", 0L)?.takeIf { it > 0L } ?: 0L
        var durationMs = extras?.getLong("extra_duration", 0L)?.takeIf { it > 0L } ?: 0L

        // MX Player / Just Player: position / duration (may be Int or Long depending on player)
        if (positionMs == 0L && durationMs == 0L) {
            @Suppress("DEPRECATION")
            positionMs = (extras?.get("position") as? Number)?.toLong()?.takeIf { it > 0L } ?: 0L
            @Suppress("DEPRECATION")
            durationMs = (extras?.get("duration") as? Number)?.toLong()?.takeIf { it > 0L } ?: 0L
        }

        val endBy = extras?.getString("end_by")

        return parseResult(positionMs, durationMs, endBy)
    }

        companion object {
            fun parseResult(positionMs: Long, durationMs: Long, endBy: String?): ExternalPlayerResult {
                if (endBy == "playback_completion") {
                    return ExternalPlayerResult(positionMs = positionMs, durationMs = durationMs, isFinished = true)
                }
                if (endBy == "exit") {
                    return ExternalPlayerResult(positionMs = positionMs, durationMs = durationMs, isFinished = false)
                }
                if (durationMs > 0) {
                    val isFinished = positionMs.toFloat() / durationMs >= 0.90f
                    return ExternalPlayerResult(positionMs = positionMs, durationMs = durationMs, isFinished = isFinished)
                }
                if (positionMs > 0) {
                    return ExternalPlayerResult(positionMs = positionMs, durationMs = 0L, isFinished = false)
                }
                return ExternalPlayerResult(positionMs = 0L, durationMs = 0L, isFinished = false)
            }
        }
}

data class ExternalPlayerResult(
    val positionMs: Long,
    val durationMs: Long,
    val isFinished: Boolean = false
)
