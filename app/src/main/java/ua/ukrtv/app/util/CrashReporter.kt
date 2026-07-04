package ua.ukrtv.app.util

import android.os.Process
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {

    private var enabled = false
    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private var crashDir: File? = null

    fun init(context: android.content.Context) {
        if (enabled) return
        crashDir = File(context.getExternalFilesDir(null), "crashes")
        crashDir?.mkdirs()
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(thread, throwable)
            originalHandler?.uncaughtException(thread, throwable)
        }
        enabled = true
    }

    private fun handleCrash(thread: Thread, throwable: Throwable) {
        try {
            val dir = crashDir ?: return
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val fileName = "crash_${dateFormat.format(Date())}.txt"
            val file = File(dir, fileName)

            FileWriter(file).use { writer ->
                writer.appendLine("=== CRASH REPORT ===")
                writer.appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
                writer.appendLine("PID: ${Process.myPid()}")
                writer.appendLine("Thread: ${thread.name} (id=${thread.id})")
                writer.appendLine("")
                writer.appendLine("=== Exception ===")
                writer.appendLine("${throwable.javaClass.name}: ${throwable.message}")
                writer.appendLine("")
                writer.appendLine("=== Stack Trace ===")
                throwable.stackTrace.forEach { element ->
                    writer.appendLine("\tat $element")
                }
                throwable.cause?.let { cause ->
                    writer.appendLine("")
                    writer.appendLine("Caused by: ${cause.javaClass.name}: ${cause.message}")
                    cause.stackTrace.forEach { element ->
                        writer.appendLine("\tat $element")
                    }
                }
                writer.appendLine("")
                writer.appendLine("=== Additional Info ===")
                writer.appendLine("Android: ${android.os.Build.VERSION.SDK_INT}")
                writer.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                writer.appendLine("Board: ${android.os.Build.BOARD}")
            }
            AppLogger.e("CrashReporter", "Crash saved to $fileName", throwable)
        } catch (e: Exception) {
            AppLogger.e("CrashReporter", "Failed to write crash report", e)
        }
    }

    fun getCrashReports(): List<File> {
        val dir = crashDir ?: return emptyList()
        return dir.listFiles()?.filter { it.name.startsWith("crash_") }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun clearCrashReports() {
        getCrashReports().forEach { it.delete() }
    }
}
