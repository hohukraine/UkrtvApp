package ua.ukrtv.app.player

import ua.ukrtv.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamHealthMonitor @Inject constructor() {

    companion object {
        private const val REBUFFER_WINDOW_MS = 60_000L
        private const val MAX_REBUFFERS_IN_WINDOW = 4
        private const val UNHEALTHY_THROUGHPUT_KBPS = 500.0
    }

    data class HealthState(
        val isHealthy: Boolean = true,
        val rebufferCount: Int = 0,
        val currentThroughputKbps: Double = 0.0,
        val reason: String = ""
    )

    private val rebufferEvents = mutableListOf<Long>()
    private var lastPosition: Long = 0L
    private var lastPositionTime: Long = 0L
    private var stalledDuration: Long = 0L

    private var _currentState = HealthState()
    val currentState: HealthState get() = _currentState

    fun onRebuffer() {
        val now = System.currentTimeMillis()
        rebufferEvents.add(now)
        trimRebufferEvents(now)
        val count = rebufferEvents.size

        AppLogger.d("StreamHealthMonitor", "Rebuffer event #$count in last ${REBUFFER_WINDOW_MS / 1000}s")

        if (count > MAX_REBUFFERS_IN_WINDOW) {
            _currentState = HealthState(
                isHealthy = false,
                rebufferCount = count,
                reason = "Too many rebuffers ($count in 60s)"
            )
        }
    }

    fun onPositionUpdate(positionMs: Long) {
        val now = System.currentTimeMillis()
        if (lastPosition > 0 && lastPositionTime > 0) {
            val dt = now - lastPositionTime
            val dp = positionMs - lastPosition
            if (dt > 0 && dp >= 0) {
                val speedFactor = dp.toDouble() / dt.toDouble()
                if (speedFactor < 0.5 && dt > 5000) {
                    stalledDuration += dt
                    if (stalledDuration > 10_000 && _currentState.isHealthy) {
                        _currentState = HealthState(
                            isHealthy = false,
                            rebufferCount = rebufferEvents.size,
                            reason = "Playback stalled for ${stalledDuration / 1000}s"
                        )
                    }
                } else {
                    stalledDuration = 0
                }
            }
        }
        lastPosition = positionMs
        lastPositionTime = now
    }

    fun onThroughputUpdate(throughputKbps: Double) {
        _currentState = _currentState.copy(currentThroughputKbps = throughputKbps)
        if (throughputKbps > 0 && throughputKbps < UNHEALTHY_THROUGHPUT_KBPS && _currentState.isHealthy) {
            _currentState = HealthState(
                isHealthy = false,
                rebufferCount = rebufferEvents.size,
                currentThroughputKbps = throughputKbps,
                reason = "Low throughput: ${"%.0f".format(throughputKbps)}KB/s"
            )
        }
    }

    fun markHealthy() {
        _currentState = HealthState()
        rebufferEvents.clear()
        stalledDuration = 0
    }

    fun markHealthyWithoutReset() {
        _currentState = HealthState(
            isHealthy = true,
            rebufferCount = 0
        )
        stalledDuration = 0
    }

    private fun trimRebufferEvents(now: Long) {
        val cutoff = now - REBUFFER_WINDOW_MS
        rebufferEvents.removeAll { it < cutoff }
    }

    fun reset() {
        _currentState = HealthState()
        rebufferEvents.clear()
        lastPosition = 0L
        lastPositionTime = 0L
        stalledDuration = 0
    }
}
