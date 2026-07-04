package ua.ukrtv.app.player

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import ua.ukrtv.app.util.AppLogger
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.util.PlayerBufferConfig
import ua.ukrtv.app.util.getDeviceClass
import ua.ukrtv.app.util.hasMediatekChipset
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerFactory @Inject constructor(
    private val codecHealthMonitor: CodecHealthMonitor
) {
    private var deviceClass: DeviceClass = DeviceClass.MID
    private var isMediatek: Boolean = hasMediatekChipset()
    private var buffers: PlayerBufferConfig.BufferParams = PlayerBufferConfig.forDevice(deviceClass, isMediatek)

    fun init(context: Context) {
        deviceClass = getDeviceClass(context)
        isMediatek = codecHealthMonitor.isMediatekDevice() || hasMediatekChipset()
        buffers = PlayerBufferConfig.forDevice(deviceClass, isMediatek)
        AppLogger.d("PlayerFactory", "Device: $deviceClass Mediatek=$isMediatek buffers=$buffers")
    }

    @OptIn(UnstableApi::class)
    fun createLoadControl() = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            buffers.minBufferMs,
            buffers.maxBufferMs,
            buffers.bufferForPlaybackMs,
            buffers.bufferForPlaybackAfterRebufferMs
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    private val hardwarePrioritySelector = MediaCodecSelector { mimeType, requiresSecureDecoder, tunneling ->
        val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, tunneling)

        if (mimeType.startsWith("video/")) {
            val preferred = all.filter { info ->
                val name = info.name.lowercase()
                !name.contains("software") && !name.contains(".sw.") &&
                !name.contains("-sw-") && !name.contains("_sw_") &&
                !codecHealthMonitor.shouldExcludeDecoder(info.name)
            }

            if (preferred.isNotEmpty()) {
                preferred.sortedByDescending { info ->
                    val name = info.name.lowercase()
                    val codecType = when {
                        name.contains("avc") || name.contains("h264") -> 10
                        name.contains("hevc") || name.contains("h265") -> 9
                        name.contains("vp9") -> 8
                        else -> 0
                    }
                    val omxBonus = if (name.contains("omx.")) 1 else 0
                    codecType + omxBonus
                }
            } else {
                all
            }
        } else {
            all
        }
    }

    @OptIn(UnstableApi::class)
    fun buildPlayer(
        context: Context,
        dataSourceFactory: DataSource.Factory
    ): ExoPlayer {
        init(context)
        val loadControl = createLoadControl()

        val renderersFactory = DefaultRenderersFactory(context.applicationContext)
            .setMediaCodecSelector(hardwarePrioritySelector)
            .setEnableDecoderFallback(true)

        val bandwidthMeter = DefaultBandwidthMeter.Builder(context.applicationContext)
            .setInitialBitrateEstimate(10_000_000) // 10 Mbps start for TV Ethernet/WiFi
            .build()

        val trackSelectionFactory = AdaptiveTrackSelection.Factory()
        val trackSelector = DefaultTrackSelector(context, trackSelectionFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(context.applicationContext)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        if (Build.VERSION.SDK_INT >= 31) {
            player.addListener(object : androidx.media3.common.Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    try {
                        val enhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId)
                        enhancer.setTargetGain(1000)
                        enhancer.enabled = true
                    } catch (e: Exception) {
                        AppLogger.w("PlayerFactory", "LoudnessEnhancer failed: ${e.message}")
                    }
                }
            })
        }

        player.addAnalyticsListener(object : AnalyticsListener {
            @Suppress("DEPRECATION", "OverridingDeprecatedMember")
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializationDurationMs: Long
            ) {
                codecHealthMonitor.onDecoderInitialized(decoderName, "video")
            }

            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) {
                codecHealthMonitor.onDroppedFrames(droppedFrames, elapsedMs)
            }

            override fun onVideoDecoderReleased(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String
            ) {
                AppLogger.d("CodecHealth", "Decoder released: $decoderName | $codecHealthMonitor")
            }

            override fun onPlayerError(
                eventTime: AnalyticsListener.EventTime,
                error: androidx.media3.common.PlaybackException
            ) {
                if (PlaybackErrorHandler.isDecodingError(error)) {
                    codecHealthMonitor.onDecoderError()
                }
            }
        })

        player.setWakeMode(PowerManager.PARTIAL_WAKE_LOCK)
        player.setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)

        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setMaxVideoSize(buffers.maxVideoSize, 1080)
                .setMaxVideoBitrate(buffers.maxVideoBitrate)
                .setPreferredAudioLanguage("ukr")
                .apply {
                    if (isMediatek) {
                        setAllowVideoMixedMimeTypeAdaptiveness(false)
                        setAllowVideoNonSeamlessAdaptiveness(false)
                    }
                    setExceedVideoConstraintsIfNecessary(true)
                }
                .build()
        )

        return player
    }
}
