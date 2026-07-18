package ua.ukrtv.app.player

import android.os.SystemClock
import androidx.media3.common.PlaybackException
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlaybackErrorHandlerTest {

    @Before
    fun setup() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1000L
    }

    private fun exception(errorCode: Int, message: String = "", cause: Throwable? = null): PlaybackException {
        return PlaybackException(message, cause, errorCode)
    }

    @Test
    fun `isNetworkError returns true for network connection failed`() {
        val e = exception(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertTrue(PlaybackErrorHandler.isNetworkError(e))
    }

    @Test
    fun `isNetworkError returns true for connection timeout`() {
        val e = exception(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        assertTrue(PlaybackErrorHandler.isNetworkError(e))
    }

    @Test
    fun `isNetworkError returns false for bad HTTP status`() {
        val e = exception(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        assertFalse(PlaybackErrorHandler.isNetworkError(e))
    }

    @Test
    fun `isNetworkError returns false for decoding error`() {
        val e = exception(PlaybackException.ERROR_CODE_DECODING_FAILED)
        assertFalse(PlaybackErrorHandler.isNetworkError(e))
    }

    @Test
    fun `isDecodingError returns true for decoding failed`() {
        val e = exception(PlaybackException.ERROR_CODE_DECODING_FAILED)
        assertTrue(PlaybackErrorHandler.isDecodingError(e))
    }

    @Test
    fun `isDecodingError returns true for decoder init failed`() {
        val e = exception(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
        assertTrue(PlaybackErrorHandler.isDecodingError(e))
    }

    @Test
    fun `isDecodingError returns true for unexpected start code prefix`() {
        val e = exception(PlaybackException.ERROR_CODE_DECODING_FAILED, "Unexpected start code prefix")
        assertTrue(PlaybackErrorHandler.isDecodingError(e))
    }

    @Test
    fun `isDecodingError returns true for OMX message`() {
        val e = exception(PlaybackException.ERROR_CODE_DECODING_FAILED, "OMX.MS.AVC.Decoder error")
        assertTrue(PlaybackErrorHandler.isDecodingError(e))
    }

    @Test
    fun `isDecodingError returns true for ACodec message`() {
        val e = exception(PlaybackException.ERROR_CODE_DECODING_FAILED, "ACodec: failed to configure")
        assertTrue(PlaybackErrorHandler.isDecodingError(e))
    }

    @Test
    fun `isDecodingError returns true for IllegalStateException cause`() {
        val e = exception(PlaybackException.ERROR_CODE_DECODING_FAILED, "", IllegalStateException("MediaCodec error"))
        assertTrue(PlaybackErrorHandler.isDecodingError(e))
    }

    @Test
    fun `isDecodingError returns false for network error`() {
        val e = exception(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertFalse(PlaybackErrorHandler.isDecodingError(e))
    }

    @Test
    fun `isUnsupportedFormat returns true for container unsupported`() {
        val e = exception(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
        assertTrue(PlaybackErrorHandler.isUnsupportedFormat(e))
    }

    @Test
    fun `isUnsupportedFormat returns true for manifest malformed`() {
        val e = exception(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED)
        assertTrue(PlaybackErrorHandler.isUnsupportedFormat(e))
    }

    @Test
    fun `isTimeout returns true for timeout error`() {
        val e = exception(PlaybackException.ERROR_CODE_TIMEOUT)
        assertTrue(PlaybackErrorHandler.isTimeout(e))
    }

    @Test
    fun `isBlockedStream returns true for 403 HTTP status`() {
        val e = exception(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, "403 Forbidden")
        assertTrue(PlaybackErrorHandler.isBlockedStream(e))
    }

    @Test
    fun `isBlockedStream returns true for 429 HTTP status`() {
        val e = exception(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, "429 Too Many Requests")
        assertTrue(PlaybackErrorHandler.isBlockedStream(e))
    }

    @Test
    fun `isBlockedStream returns false for 404`() {
        val e = exception(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, "404 Not Found")
        assertFalse(PlaybackErrorHandler.isBlockedStream(e))
    }

    @Test
    fun `shouldFallbackStream returns true for unsupported format`() {
        val e = exception(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
        assertTrue(PlaybackErrorHandler.shouldFallbackStream(e))
    }

    @Test
    fun `shouldFallbackStream returns true for decoding error`() {
        val e = exception(PlaybackException.ERROR_CODE_DECODING_FAILED)
        assertTrue(PlaybackErrorHandler.shouldFallbackStream(e))
    }

    @Test
    fun `shouldFallbackStream returns false for network error`() {
        val e = exception(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertFalse(PlaybackErrorHandler.shouldFallbackStream(e))
    }

    @Test
    fun `shouldRetry returns true for network error`() {
        val e = exception(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertTrue(PlaybackErrorHandler.shouldRetry(e))
    }

    @Test
    fun `shouldRetry returns true for timeout`() {
        val e = exception(PlaybackException.ERROR_CODE_TIMEOUT)
        assertTrue(PlaybackErrorHandler.shouldRetry(e))
    }

    @Test
    fun `shouldRetry returns false for decoding error`() {
        val e = exception(PlaybackException.ERROR_CODE_DECODING_FAILED)
        assertFalse(PlaybackErrorHandler.shouldRetry(e))
    }

    @Test
    fun `getErrorCategory returns NETWORK`() {
        val e = exception(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertEquals(PlaybackErrorHandler.ErrorCategory.NETWORK, PlaybackErrorHandler.getErrorCategory(e))
    }

    @Test
    fun `getErrorCategory returns DECODER`() {
        val e = exception(PlaybackException.ERROR_CODE_DECODING_FAILED)
        assertEquals(PlaybackErrorHandler.ErrorCategory.DECODER, PlaybackErrorHandler.getErrorCategory(e))
    }

    @Test
    fun `getErrorCategory returns UNSUPPORTED_FORMAT`() {
        val e = exception(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
        assertEquals(PlaybackErrorHandler.ErrorCategory.UNSUPPORTED_FORMAT, PlaybackErrorHandler.getErrorCategory(e))
    }

    @Test
    fun `getErrorCategory returns TIMEOUT`() {
        val e = exception(PlaybackException.ERROR_CODE_TIMEOUT)
        assertEquals(PlaybackErrorHandler.ErrorCategory.TIMEOUT, PlaybackErrorHandler.getErrorCategory(e))
    }

    @Test
    fun `getErrorCategory returns BLOCKED for unknown code with 403 message`() {
        val e = exception(Int.MAX_VALUE, "403 Forbidden")
        assertEquals(PlaybackErrorHandler.ErrorCategory.BLOCKED, PlaybackErrorHandler.getErrorCategory(e))
    }

    @Test
    fun `getErrorCategory returns UNKNOWN`() {
        val e = exception(Int.MAX_VALUE)
        assertEquals(PlaybackErrorHandler.ErrorCategory.UNKNOWN, PlaybackErrorHandler.getErrorCategory(e))
    }

    @Test
    fun `getUserMessage returns Ukrainian message for NETWORK`() {
        val e = exception(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertTrue(PlaybackErrorHandler.getUserMessage(e).contains("мережі"))
    }

    @Test
    fun `getUserMessage returns Ukrainian message for DECODER`() {
        val e = exception(PlaybackException.ERROR_CODE_DECODING_FAILED)
        assertTrue(PlaybackErrorHandler.getUserMessage(e).contains("декодування"))
    }
}
