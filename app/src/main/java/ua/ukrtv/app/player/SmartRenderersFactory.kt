package ua.ukrtv.app.player

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class SmartRenderersFactory @Inject constructor(
    context: Context,
    private val codecSelector: SmartMediaCodecSelector
) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        allowedJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        Log.d("SmartRenderersFactory", "Building video renderers with policy=${codecSelector.getPolicy()}")
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            this.codecSelector,
            enableDecoderFallback,
            eventHandler,
            videoRendererEventListener,
            allowedJoiningTimeMs,
            out
        )
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        audioRendererEventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        Log.d("SmartRenderersFactory", "Building audio renderers with policy=${codecSelector.getPolicy()}")
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            this.codecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            audioRendererEventListener,
            out
        )
    }
}
