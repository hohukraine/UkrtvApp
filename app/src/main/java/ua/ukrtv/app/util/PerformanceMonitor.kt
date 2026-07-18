package ua.ukrtv.app.util

import android.os.Trace

object PerformanceMonitor {

    fun begin(name: String) = Trace.beginSection(name)

    fun end() = Trace.endSection()

    inline fun <T> traceResult(sectionName: String, action: () -> T): T {
        begin(sectionName)
        try {
            return action()
        } finally {
            end()
        }
    }
}
