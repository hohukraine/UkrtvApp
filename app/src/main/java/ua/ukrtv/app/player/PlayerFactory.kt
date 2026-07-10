package ua.ukrtv.app.player

import android.content.Context
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
    private val codecHealthMonitor: CodecHealthMonitor,
    private val codecPreferences: CodecPreferences
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
        val codecTier = codecPreferences.getCodecTier()

        if (mimeType.startsWith("video/")) {
            val filtered = all.filter { info ->
                val name = info.name.lowercase()
                val passesHealth = !codecHealthMonitor.shouldExcludeDecoder(info.name)
                val passesTier = when (codecTier) {
                    CodecTier.AUTO -> true
                    CodecTier.H264 -> name.contains("avc") || name.contains("h264")
                    CodecTier.HARDWARE_ONLY -> {
                        !name.contains("software") && !name.contains(".sw.") &&
                        !name.contains("-sw-") && !name.contains("_sw_")
                    }
                }
                passesHealth && passesTier
            }

            if (filtered.isNotEmpty()) {
                filtered.sortedByDescending { info ->
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
            .setInitialBitrateEstimate(
                when (deviceClass) {
                    DeviceClass.LOW -> 10_000_000L
                    DeviceClass.MID -> 25_000_000L
                    DeviceClass.HIGH -> 40_000_000L
                }
            )
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

        player.addAnalyticsListener(object : AnalyticsListener {
            @Suppress("DEPRECATION")
            @Deprecated("Superseded by new Media3 listener API", level = DeprecationLevel.HIDDEN)
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
                .setMaxVideoSize(1920, 1080)
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
