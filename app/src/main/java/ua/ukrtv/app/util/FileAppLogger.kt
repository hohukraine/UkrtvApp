package ua.ukrtv.app.util

import android.content.Context
import android.os.Environment
import android.os.Process
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object FileAppLogger {
    private const val MAX_LOG_SIZE_BYTES = 2 * 1024 * 1024
    private const val MAX_BACKUP_FILES = 3
    private var writer: FileWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null
    private var initialized = false
    private val fileSize = AtomicLong(0)

    fun init(context: Context) {
        if (initialized) return
        try {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) logDir.mkdirs()
            logFile = File(logDir, "ukrtv_app.log")
            rotateIfNeeded()
            writer = FileWriter(logFile, true)
            initialized = true
            log("FileAppLogger", "Logger initialized, pid=${Process.myPid()}")
        } catch (e: Exception) {
            android.util.Log.w("UkrtvApp:FileLogger", "Init failed: ${e.message}")
        }
    }

    fun log(tag: String, message: String) {
        try {
            if (!initialized || writer == null) return
            val timestamp = dateFormat.format(Date())
            val line = "$timestamp D/$tag: $message\n"
            synchronized(this) {
                writer?.append(line)
                writer?.flush()
                val newSize = fileSize.addAndGet(line.length.toLong())
                if (newSize > MAX_LOG_SIZE_BYTES) rotateIfNeeded()
            }
        } catch (_: IOException) {}
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        log("E:$tag", message + (throwable?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    private fun rotateIfNeeded() {
        val current = logFile ?: return
        try {
            if (!current.exists() || current.length() < MAX_LOG_SIZE_BYTES) return
            for (i in MAX_BACKUP_FILES - 1 downTo 1) {
                val src = File(current.parent, "ukrtv_app.log.$i")
                val dst = File(current.parent, "ukrtv_app.log.${i + 1}")
                if (src.exists()) src.renameTo(dst)
            }
            if (current.exists()) {
                current.renameTo(File(current.parent, "ukrtv_app.log.1"))
            }
            fileSize.set(0)
        } catch (_: Exception) {}
    }
}
