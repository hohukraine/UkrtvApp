package ua.ukrtv.app.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import ua.ukrtv.app.Constants
import ua.ukrtv.app.util.PlayerBufferConfig
import ua.ukrtv.app.util.getDeviceClass
import ua.ukrtv.app.util.hasMediatekChipset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddedPlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent(Constants.USER_AGENT)

    private val deviceClass = getDeviceClass(context)
    private val isMediatek = hasMediatekChipset()
    private val buffers = PlayerBufferConfig.forDevice(deviceClass, isMediatek)

    @OptIn(UnstableApi::class)
    fun createPlayer(thermalLevel: ThermalMonitor.QualityLevel = ThermalMonitor.QualityLevel.HIGH): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                buffers.minBufferMs,
                buffers.maxBufferMs,
                buffers.bufferForPlaybackMs,
                buffers.bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setMediaCodecSelector(hardwarePrioritySelector)
            .setEnableDecoderFallback(true)

        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(
                when (deviceClass) {
                    ua.ukrtv.app.util.DeviceClass.LOW -> 10_000_000L
                    ua.ukrtv.app.util.DeviceClass.MID -> 25_000_000L
                    ua.ukrtv.app.util.DeviceClass.HIGH -> 40_000_000L
                }
            )
            .build()

        val trackSelector = DefaultTrackSelector(context, AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs= */ 1000,
            /* maxDurationForQualityDecreaseMs= */ 10000,
            /* minDurationToRetainAfterDiscardMs= */ 25000,
            /* bandwidthFraction= */ 0.75f
        ))
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setMaxVideoSize(buffers.maxVideoSize, buffers.maxVideoSize)
                .setMaxVideoBitrate(buffers.maxVideoBitrate)
                .setTunnelingEnabled(false)
                .setPreferredAudioLanguage("ukr")
                .also { applyThermalConstraints(it, thermalLevel) }
                .apply {
                    if (isMediatek) {
                        setAllowVideoMixedMimeTypeAdaptiveness(false)
                    }
                    setExceedVideoConstraintsIfNecessary(true)
                }
                .build()
        )

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    fun applyThermalToPlayer(player: ExoPlayer, thermalLevel: ThermalMonitor.QualityLevel) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .also { applyThermalConstraints(it, thermalLevel) }
            .build()
    }

    private fun applyThermalConstraints(
        builder: TrackSelectionParameters.Builder,
        level: ThermalMonitor.QualityLevel
    ) {
        when (level) {
            ThermalMonitor.QualityLevel.MINIMAL -> {
                builder.setMaxVideoSize(640, 360)
                builder.setMaxVideoBitrate(800_000)
            }
            ThermalMonitor.QualityLevel.LOW -> {
                builder.setMaxVideoSize(1280, 720)
                builder.setMaxVideoBitrate(3_000_000)
            }
            ThermalMonitor.QualityLevel.MEDIUM -> {
                builder.setMaxVideoSize(1920, 1080)
                builder.setMaxVideoBitrate(8_000_000)
            }
            ThermalMonitor.QualityLevel.HIGH -> {
                builder.clearVideoSizeConstraints()
                builder.setMaxVideoBitrate(Int.MAX_VALUE)
            }
        }
    }

    private val hardwarePrioritySelector = MediaCodecSelector { mimeType, requiresSecureDecoder, tunneling ->
        val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, tunneling)

        if (mimeType.startsWith("video/")) {
            val filtered = all.filter { info ->
                val name = info.name.lowercase()
                val passesHealth = !(name.contains("omx.ms.") && name.contains("avc") && !isMediatek)
                passesHealth
            }

            if (filtered.isNotEmpty()) {
                filtered.sortedByDescending { info ->
                    val name = info.name.lowercase()
                    val codecType = when {
                        name.contains("hevc") || name.contains("h265") -> 10
                        name.contains("vp9") -> 9
                        name.contains("avc") || name.contains("h264") -> 8
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

    /** Applies request headers (e.g. Referer) for the next stream; providers require them. */
    @OptIn(UnstableApi::class)
    fun setDefaultRequestProperties(properties: Map<String, String>) {
        dataSourceFactory.setDefaultRequestProperties(properties)
    }
}
