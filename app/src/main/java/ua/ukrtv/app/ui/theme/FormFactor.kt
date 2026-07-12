package ua.ukrtv.app.ui.theme

import android.content.Context
import android.app.UiModeManager
import android.content.res.Configuration
import androidx.compose.runtime.compositionLocalOf

enum class FormFactor { TV, PHONE, TABLET }

val LocalFormFactor = compositionLocalOf { FormFactor.TV }

fun detectFormFactor(context: Context): FormFactor {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val modeType = uiModeManager?.currentModeType ?: Configuration.UI_MODE_TYPE_NORMAL
    return when (modeType) {
        Configuration.UI_MODE_TYPE_TELEVISION -> FormFactor.TV
        else -> {
            if (context.resources.configuration.smallestScreenWidthDp >= 600) {
                FormFactor.TABLET
            } else {
                FormFactor.PHONE
            }
        }
    }
}
