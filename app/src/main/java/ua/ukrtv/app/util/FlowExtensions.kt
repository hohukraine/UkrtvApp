package ua.ukrtv.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Chunks a flow into lists of elements.
 * 
 * @param size The maximum size of each chunk.
 * @param durationMillis The maximum duration to wait before emitting a chunk, even if it hasn't reached the specified size.
 */
fun <T> Flow<T>.chunked(size: Int, durationMillis: Long): Flow<List<T>> = channelFlow {
    val buffer = mutableListOf<T>()
    var timerJob: Job? = null

    fun flush() {
        timerJob?.cancel()
        timerJob = null
        if (buffer.isNotEmpty()) {
            val items = buffer.toList()
            buffer.clear()
            // We use launch to ensure we don't block the collection of the source flow
            launch { send(items) }
        }
    }

    collect { value ->
        buffer.add(value)
        if (buffer.size >= size) {
            flush()
        } else if (timerJob == null) {
            timerJob = launch {
                delay(durationMillis)
                flush()
            }
        }
    }
    // Final flush after source flow completes
    flush()
}
