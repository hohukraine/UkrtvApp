package ua.ukrtv.app.player

import android.util.Log
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import javax.inject.Inject
import javax.inject.Singleton
import androidx.annotation.OptIn

@UnstableApi
@Singleton
class SmartMediaCodecSelector @Inject constructor() : MediaCodecSelector {

    private var policy: CodecPolicy = CodecPolicy.AUTO

    private val defaultSelector = MediaCodecSelector.DEFAULT

    fun setPolicy(newPolicy: CodecPolicy) {
        policy = newPolicy
    }

    fun getPolicy(): CodecPolicy = policy

    @OptIn(UnstableApi::class)
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val candidates = defaultSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        
        val sorted = when (policy) {
            CodecPolicy.SOFTWARE_FIRST -> {
                val swDecoders = candidates.filter { 
                    it.name.startsWith("OMX.google.") || it.name.startsWith("c2.android.") 
                }
                if (swDecoders.isNotEmpty()) {
                    swDecoders
                } else {
                    // Fallback to hardware if no software decoders found (should not happen for standard mimes)
                    candidates.sortedBy { it.hardwareAccelerated }
                }
            }
            else -> {
                candidates.sortedWith(compareByDescending<MediaCodecInfo> { 
                    it.hardwareAccelerated 
                }.thenBy { 
                    if (it.mimeType == MimeTypes.VIDEO_H264) 0 else 1
                })
            }
        }
        
        sorted.firstOrNull()?.let { 
            logCodecInfo(it, mimeType)
        }
        
        return sorted
    }

    private fun logCodecInfo(info: MediaCodecInfo, mimeType: String) {
        val name = info.name
        val hw = if (info.hardwareAccelerated) "HW" else "SW"
        val kind = if (mimeType.startsWith("video/", ignoreCase = true)) "Video" else "Audio"
        Log.d("SmartCodecSelector", "Selected codec: name=$name, mime=$mimeType, hw=$hw, kind=$kind, policy=$policy")
    }
}
