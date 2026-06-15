package ua.ukrtv.app.player

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import javax.inject.Inject
import javax.inject.Singleton
import androidx.annotation.OptIn

@UnstableApi
@Singleton
class PlayerFactory @Inject constructor(private val codecSelector: SmartMediaCodecSelector) {

    @OptIn(UnstableApi::class)
    fun createLoadControl() = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            30_000,  // minBufferMs (30s as per AGENTS.md)
            120_000, // maxBufferMs (120s as per AGENTS.md)
            2_500,   // bufferForPlaybackMs
            5_000    // bufferForPlaybackAfterRebufferMs
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    @OptIn(UnstableApi::class)
    fun createMediaSourceFactory(
        dataSourceFactory: OkHttpDataSource.Factory
    ): MediaSource.Factory {
        return DefaultMediaSourceFactory(dataSourceFactory).apply {
            setDataSourceFactory(dataSourceFactory)
        }
    }

    @OptIn(UnstableApi::class)
    fun buildPlayer(
        context: Context,
        dataSourceFactory: OkHttpDataSource.Factory,
        codecPolicy: CodecPolicy = CodecPolicy.AUTO
    ): ExoPlayer {
        codecSelector.setPolicy(codecPolicy)
        val loadControl = createLoadControl()
        val mediaSourceFactory = createMediaSourceFactory(dataSourceFactory)
        val renderersFactory = SmartRenderersFactory(context.applicationContext, codecSelector)
            .setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(context.applicationContext, renderersFactory)
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
            .build()

        return player
    }
}
