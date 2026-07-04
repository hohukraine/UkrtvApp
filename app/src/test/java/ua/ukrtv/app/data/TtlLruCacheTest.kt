package ua.ukrtv.app.data

import org.junit.Assert.*
import org.junit.Test

class TtlLruCacheTest {

    @Test
    fun `put and get within TTL returns value`() {
        val cache = TtlLruCache<String, String>(maxSize = 10, ttlMs = 5000)
        cache.put("key1", "value1", nowMs = 1000)
        assertEquals("value1", cache.get("key1", nowMs = 2000))
    }

    @Test
    fun `expired entry returns null`() {
        val cache = TtlLruCache<String, String>(maxSize = 10, ttlMs = 1000)
        cache.put("key", "value", nowMs = 1000)
        assertNull(cache.get("key", nowMs = 3000))
    }

    @Test
    fun `missing key returns null`() {
        val cache = TtlLruCache<String, String>(maxSize = 10, ttlMs = 5000)
        assertNull(cache.get("nonexistent"))
    }

    @Test
    fun `evicts eldest when maxSize exceeded`() {
        val cache = TtlLruCache<Int, String>(maxSize = 3, ttlMs = 50000)
        cache.put(1, "a", nowMs = 1000)
        cache.put(2, "b", nowMs = 2000)
        cache.put(3, "c", nowMs = 3000)
        cache.put(4, "d", nowMs = 4000)
        assertNull(cache.get(1, nowMs = 5000))
        assertEquals("b", cache.get(2, nowMs = 5000))
        assertEquals("c", cache.get(3, nowMs = 5000))
        assertEquals("d", cache.get(4, nowMs = 5000))
    }

    @Test
    fun `invalidate removes specific key`() {
        val cache = TtlLruCache<String, String>(maxSize = 10, ttlMs = 50000)
        val now = 1000L
        cache.put("key1", "value1", nowMs = now)
        cache.put("key2", "value2", nowMs = now)
        cache.invalidate("key1")
        assertNull(cache.get("key1", nowMs = now))
        assertEquals("value2", cache.get("key2", nowMs = now))
    }

    @Test
    fun `invalidateIf removes matching keys`() {
        val cache = TtlLruCache<String, String>(maxSize = 10, ttlMs = 50000)
        val now = 1000L
        cache.put("a:1", "v1", nowMs = now)
        cache.put("b:2", "v2", nowMs = now)
        cache.put("a:3", "v3", nowMs = now)
        cache.invalidateIf { it.startsWith("a:") }
        assertNull(cache.get("a:1", nowMs = now))
        assertNull(cache.get("a:3", nowMs = now))
        assertEquals("v2", cache.get("b:2", nowMs = now))
    }

    @Test
    fun `clear removes all entries`() {
        val cache = TtlLruCache<String, String>(maxSize = 10, ttlMs = 50000)
        val now = 1000L
        cache.put("k1", "v1", nowMs = now)
        cache.put("k2", "v2", nowMs = now)
        cache.clear()
        assertNull(cache.get("k1", nowMs = now))
        assertNull(cache.get("k2", nowMs = now))
    }

    @Test
    fun `put updates existing key`() {
        val cache = TtlLruCache<String, String>(maxSize = 10, ttlMs = 5000)
        cache.put("key", "old", nowMs = 1000)
        cache.put("key", "new", nowMs = 2000)
        assertEquals("new", cache.get("key", nowMs = 3000))
    }
}
