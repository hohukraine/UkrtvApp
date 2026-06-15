package ua.ukrtv.app.data.api

import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class CloudflareState @Inject constructor() {
    private val blockedDomains = ConcurrentHashMap<String, Long>()

    fun markBlocked(host: String, durationMs: Long = 5 * 60 * 1000L) {
        blockedDomains[host] = System.currentTimeMillis() + durationMs
    }

    fun markResolved(host: String) {
        blockedDomains.remove(host)
    }

    fun isBlocked(host: String): Boolean {
        val blockedUntil = blockedDomains[host] ?: return false
        return if (System.currentTimeMillis() < blockedUntil) {
            true
        } else {
            blockedDomains.remove(host)
            false
        }
    }
}