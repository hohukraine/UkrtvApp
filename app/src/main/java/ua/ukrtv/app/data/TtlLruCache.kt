package ua.ukrtv.app.data

import java.util.LinkedHashMap

class TtlLruCache<K, V>(
    private val maxSize: Int,
    private val ttlMs: Long,
) {
    private data class Entry<V>(val value: V, val cachedAtMs: Long)

    private val map = object : LinkedHashMap<K, Entry<V>>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(key: K, nowMs: Long = System.currentTimeMillis()): V? {
        val entry = map[key] ?: return null
        if (nowMs - entry.cachedAtMs > ttlMs) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V, nowMs: Long = System.currentTimeMillis()) {
        map[key] = Entry(value, nowMs)
    }

    @Synchronized
    fun invalidate(key: K) {
        map.remove(key)
    }

    @Synchronized
    fun invalidateIf(predicate: (K) -> Boolean) {
        val it = map.entries.iterator()
        while (it.hasNext()) {
            if (predicate(it.next().key)) {
                it.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}
