package ua.ukrtv.app.player

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class CodecTier(val label: String) {
    AUTO("Auto"),
    H264("H.264 Only"),
    HARDWARE_ONLY("Hardware Only")
}

@Singleton
class CodecPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("codec_prefs", Context.MODE_PRIVATE)

    fun getCodecTier(): CodecTier {
        val name = prefs.getString(KEY_CODEC_TIER, CodecTier.AUTO.name) ?: CodecTier.AUTO.name
        return try { CodecTier.valueOf(name) } catch (_: IllegalArgumentException) { CodecTier.AUTO }
    }

    fun setCodecTier(tier: CodecTier) {
        prefs.edit().putString(KEY_CODEC_TIER, tier.name).apply()
    }

    companion object {
        private const val KEY_CODEC_TIER = "codec_tier"
    }
}
