package ua.ukrtv.app.util

import android.util.Log

object AppLogger {

    private const val DEFAULT_TAG = "UkrtvApp"
    private var isDebug = true

    fun init(debug: Boolean) {
        isDebug = debug
    }

    fun d(tag: String, message: String) {
        if (isDebug) Log.d("$DEFAULT_TAG:$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$DEFAULT_TAG:$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w("$DEFAULT_TAG:$tag", message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$DEFAULT_TAG:$tag", message, throwable)
    }

    fun stream(tag: String, event: String, data: Map<String, String> = emptyMap()) {
        val dataStr = if (data.isNotEmpty()) " | ${data.entries.joinToString(", ")}" else ""
        d("Stream:$tag", "$event$dataStr")
    }
}
