package ua.ukrtv.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import ua.ukrtv.app.util.DeviceClass

val LocalDeviceClass = compositionLocalOf { DeviceClass.MID }
val LocalIsMediatek = compositionLocalOf { false }
