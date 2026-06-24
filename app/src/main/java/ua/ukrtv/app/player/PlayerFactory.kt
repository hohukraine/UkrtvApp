package ua.ukrtv.app.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import javax.inject.Inject
import javax.inject.Singleton
import androidx.annotation.OptIn

@UnstableApi
@Singleton
class PlayerFactory @Inject constructor(private val codecSelector: SmartMediaCodecSelector) {

    @OptIn(UnstableApi::class)
    fun createLoadControl() = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            30_000,
            120_000,
            2_500,
            5_000
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    @OptIn(UnstableApi::class)
    fun createLoadControlForMobile(
        minBufferMs: Int = 15_000,
        maxBufferMs: Int = 60_000,
        bufferForPlaybackMs: Int = 1_500,
        bufferForPlaybackAfterRebufferMs: Int = 3_000
    ) = DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    @OptIn(UnstableApi::class)
    fun createMediaSourceFactory(
        dataSourceFactory: DataSource.Factory
    ): MediaSource.Factory {
        return DefaultMediaSourceFactory(dataSourceFactory).apply {
            setDataSourceFactory(dataSourceFactory)
        }
    }

    @OptIn(UnstableApi::class)
    fun buildPlayer(
        context: Context,
        dataSourceFactory: DataSource.Factory,
        codecPolicy: CodecPolicy = CodecPolicy.AUTO,
        trackSelector: DefaultTrackSelector? = null
    ): ExoPlayer {
        codecSelector.setPolicy(codecPolicy)
        val loadControl = createLoadControl()
        val mediaSourceFactory = createMediaSourceFactory(dataSourceFactory)
        val renderersFactory = SmartRenderersFactory(context.applicationContext, codecSelector)
            .setEnableDecoderFallback(true)

        val builder = ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setLooper(android.os.Looper.getMainLooper())
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)

        if (trackSelector != null) {
            builder.setTrackSelector(trackSelector)
        }

        return builder.build()
    }

    @OptIn(UnstableApi::class)
    fun buildPlayerWithMobileBuffers(
        context: Context,
        dataSourceFactory: DataSource.Factory,
        codecPolicy: CodecPolicy = CodecPolicy.AUTO,
        trackSelector: DefaultTrackSelector? = null,
        minBufferMs: Int = 15_000,
        maxBufferMs: Int = 60_000
    ): ExoPlayer {
        codecSelector.setPolicy(codecPolicy)
        val loadControl = createLoadControlForMobile(
            minBufferMs = minBufferMs,
            maxBufferMs = maxBufferMs
        )
        val mediaSourceFactory = createMediaSourceFactory(dataSourceFactory)
        val renderersFactory = SmartRenderersFactory(context.applicationContext, codecSelector)
            .setEnableDecoderFallback(true)

        val builder = ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setLooper(android.os.Looper.getMainLooper())
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)

        if (trackSelector != null) {
            builder.setTrackSelector(trackSelector)
        }

        return builder.build()
    }
}
