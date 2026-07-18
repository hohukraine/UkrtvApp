package ua.ukrtv.app.data.streaming

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.util.ResolutionLogger

class ResolutionPipelineTest {

    private lateinit var logger: ResolutionLogger

    private class FakeStrategy(
        override val name: String,
        private val canHandleResult: Boolean = true,
        private val resolveResult: StreamResolutionResult? = null,
        private val resolveException: Exception? = null
    ) : ResolutionStrategy {
        var canHandleCallCount = 0
        var resolveCallCount = 0

        override suspend fun canHandle(url: String, context: ResolutionContext): Boolean {
            canHandleCallCount++
            return canHandleResult
        }

        override suspend fun resolve(url: String, context: ResolutionContext): StreamResolutionResult? {
            resolveCallCount++
            resolveException?.let { throw it }
            return resolveResult
        }
    }

    @Before
    fun setUp() {
        logger = ResolutionLogger()
    }

    @Test
    fun `resolve returns first successful result`() = runTest {
        val strategy1 = FakeStrategy("First", resolveResult = null)
        val strategy2 = FakeStrategy("Second", resolveResult = makeResult("https://result.m3u8"))
        val chain = ResolutionChain(listOf(strategy1, strategy2), logger)

        val result = chain.resolve("https://example.com", ResolutionContext())

        assertNotNull(result)
        assertEquals("https://result.m3u8", result!!.streamUrl)
        assertEquals(1, strategy1.resolveCallCount)
        assertEquals(1, strategy2.resolveCallCount)
    }

    @Test
    fun `resolve skips strategies that cannot handle`() = runTest {
        val strategy1 = FakeStrategy("CannotHandle", canHandleResult = false)
        val strategy2 = FakeStrategy("CanHandle", resolveResult = makeResult("https://result.m3u8"))
        val chain = ResolutionChain(listOf(strategy1, strategy2), logger)

        val result = chain.resolve("https://example.com", ResolutionContext())

        assertNotNull(result)
        assertEquals(0, strategy1.resolveCallCount)
        assertEquals(1, strategy2.resolveCallCount)
    }

    @Test
    fun `resolve returns null when all strategies return null`() = runTest {
        val strategy1 = FakeStrategy("First", resolveResult = null)
        val strategy2 = FakeStrategy("Second", resolveResult = null)
        val chain = ResolutionChain(listOf(strategy1, strategy2), logger)

        val result = chain.resolve("https://example.com", ResolutionContext())

        assertNull(result)
    }

    @Test
    fun `resolve continues after strategy throws exception`() = runTest {
        val strategy1 = FakeStrategy("Throws", resolveException = RuntimeException("boom"))
        val strategy2 = FakeStrategy("Works", resolveResult = makeResult("https://result.m3u8"))
        val chain = ResolutionChain(listOf(strategy1, strategy2), logger)

        val result = chain.resolve("https://example.com", ResolutionContext())

        assertNotNull(result)
        assertEquals("https://result.m3u8", result!!.streamUrl)
    }

    @Test
    fun `resolve rethrows last exception when all strategies fail`() = runTest {
        val strategy = FakeStrategy("Fails", resolveException = RuntimeException("boom"))
        val chain = ResolutionChain(listOf(strategy), logger)

        try {
            chain.resolve("https://example.com", ResolutionContext())
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("boom", e.message)
        }
    }

    @Test
    fun `resolve with empty strategies returns null`() = runTest {
        val chain = ResolutionChain(emptyList(), logger)
        val result = chain.resolve("https://example.com", ResolutionContext())
        assertNull(result)
    }

    @Test
    fun `resolve returns first successful and stops`() = runTest {
        val strategy1 = FakeStrategy("First", resolveResult = makeResult("https://first.m3u8"))
        val strategy2 = FakeStrategy("Second", resolveResult = makeResult("https://second.m3u8"))
        val chain = ResolutionChain(listOf(strategy1, strategy2), logger)

        val result = chain.resolve("https://example.com", ResolutionContext())

        assertNotNull(result)
        assertEquals("https://first.m3u8", result!!.streamUrl)
        assertEquals(1, strategy1.resolveCallCount)
        assertEquals(0, strategy2.resolveCallCount)
    }

    // --- ResolutionContext ---

    @Test
    fun `ResolutionContext defaults`() {
        val ctx = ResolutionContext()
        assertNull(ctx.season)
        assertNull(ctx.episode)
        assertNull(ctx.voiceover)
        assertFalse(ctx.isDeep)
        assertEquals("", ctx.referer)
        assertNull(ctx.prefetchedHtml)
    }

    @Test
    fun `ResolutionContext with values`() {
        val ctx = ResolutionContext(
            season = 2,
            episode = 3,
            voiceover = "UA",
            isDeep = true,
            referer = "https://referer.com",
            prefetchedHtml = "<html></html>"
        )
        assertEquals(2, ctx.season)
        assertEquals(3, ctx.episode)
        assertEquals("UA", ctx.voiceover)
        assertTrue(ctx.isDeep)
        assertEquals("https://referer.com", ctx.referer)
        assertEquals("<html></html>", ctx.prefetchedHtml)
    }

    private fun makeResult(url: String) = StreamResolutionResult(
        streamUrl = url,
        streamType = StreamType.HLS,
        referer = "",
        sourcePageUrl = "https://example.com"
    )
}
