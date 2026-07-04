package ua.ukrtv.app.util

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object AppLogger {

    private const val DEFAULT_TAG = "UkrtvApp"
    private var isDebug = true
    private var fileLoggerInitialized = false

    fun init(context: android.content.Context) {
        if (!fileLoggerInitialized) {
            FileAppLogger.init(context)
            fileLoggerInitialized = true
        }
    }

    fun d(tag: String, message: String) {
        if (isDebug) Log.d("$DEFAULT_TAG:$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$DEFAULT_TAG:$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w("$DEFAULT_TAG:$tag", message, throwable)
        FileAppLogger.logError("W:$tag", message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$DEFAULT_TAG:$tag", message, throwable)
        FileAppLogger.logError("E:$tag", message, throwable)
    }

    fun perf(tag: String, label: String, startMs: Long) {
        val elapsed = System.currentTimeMillis() - startMs
        d("Perf:$tag", "$label took ${elapsed}ms")
    }

}

object Perf {
    private val starts = ConcurrentHashMap<String, Long>()

    fun start(key: String) {
        starts[key] = System.currentTimeMillis()
    }

    fun end(key: String, tag: String = "Perf") {
        val start = starts.remove(key) ?: return
        val elapsed = System.currentTimeMillis() - start
        AppLogger.d(tag, "$key completed in ${elapsed}ms")
    }
}
