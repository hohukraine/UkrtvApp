package ua.ukrtv.app.util

import android.os.Trace

object PerformanceMonitor {

    private var enabled = true

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun begin(name: String) {
        if (enabled) Trace.beginSection(name)
    }

    fun end() {
        if (enabled) Trace.endSection()
    }

    inline fun trace(sectionName: String, action: () -> Unit) {
        begin(sectionName)
        try {
            action()
        } finally {
            end()
        }
    }

    inline fun <T> traceResult(sectionName: String, action: () -> T): T {
        begin(sectionName)
        try {
            return action()
        } finally {
            end()
        }
    }
}
