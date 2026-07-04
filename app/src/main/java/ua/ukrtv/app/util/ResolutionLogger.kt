package ua.ukrtv.app.util

import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolutionLogger @Inject constructor() {
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val maxLogs = 50

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val url: String,
        val strategy: String,
        val message: String,
        val isError: Boolean = false
    )

    fun log(url: String, strategy: String, message: String, isError: Boolean = false) {
        logs.add(LogEntry(url = url, strategy = strategy, message = message, isError = isError))
        while (logs.size > maxLogs) {
            logs.poll()
        }
        val prefix = if (isError) "❌" else "✅"
        AppLogger.d("ResolutionAudit", "$prefix [$strategy] $url: $message")
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun getFormattedLogs(): String {
        return logs.joinToString("\n") { entry ->
            val date = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(entry.timestamp)
            "[$date] ${if (entry.isError) "ERR" else "OK "} [${entry.strategy}] ${entry.url}: ${entry.message}"
        }
    }
}
