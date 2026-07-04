package ua.ukrtv.app.player

import androidx.media3.common.PlaybackException

object PlaybackErrorHandler {

    fun isNetworkError(error: PlaybackException): Boolean {
        val code = error.errorCode
        return code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    }

    fun isUnsupportedFormat(error: PlaybackException): Boolean {
        val code = error.errorCode
        return code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
            code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
    }

    fun isParsingError(error: PlaybackException): Boolean {
        val msg = (error.message ?: "").lowercase()
        return isUnsupportedFormat(error) ||
            msg.contains("start code") ||
            msg.contains("malformed") ||
            msg.contains("unexpected") ||
            msg.contains("discarded an unknown buffer") ||
            msg.contains("ts parser") ||
            msg.contains("pes packet")
    }

    fun isTimeout(error: PlaybackException): Boolean {
        val code = error.errorCode
        return code == PlaybackException.ERROR_CODE_TIMEOUT ||
            code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
    }

    fun isDecodingError(error: PlaybackException): Boolean {
        val code = error.errorCode
        val msg = (error.message ?: "").lowercase()
        return code == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            code == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
            code == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
            // Mediatek-specific: black screen but no error code
            (code == PlaybackException.ERROR_CODE_REMOTE_ERROR && msg.contains("codec")) ||
            msg.contains("c2.mtk.hevc") && msg.contains("init")
    }

    fun isMediatekBlackScreen(error: PlaybackException): Boolean {
        // Mediatek HEVC decoders sometimes freeze without throwing explicit errors.
        // Detect via frame-drop pattern in CodecHealthMonitor instead.
        return false
    }

    fun isBlockedStream(error: PlaybackException): Boolean {
        val code = error.errorCode
        val httpStatusHint = error.message ?: ""
        return code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
            (httpStatusHint.contains("403", ignoreCase = true) ||
                httpStatusHint.contains("429", ignoreCase = true) ||
                httpStatusHint.contains("forbidden", ignoreCase = true) ||
                httpStatusHint.contains("too many requests", ignoreCase = true)) ||
            httpStatusHint.contains("403", ignoreCase = true) ||
            httpStatusHint.contains("429", ignoreCase = true) ||
            httpStatusHint.contains("Forbidden", ignoreCase = true)
    }

    fun shouldFallbackStream(error: PlaybackException): Boolean {
        return isUnsupportedFormat(error) || isDecodingError(error) || isParsingError(error)
    }

    fun shouldRetry(error: PlaybackException): Boolean {
        return isNetworkError(error) || isTimeout(error)
    }

    fun getErrorCategory(error: PlaybackException): ErrorCategory {
        return when {
            isNetworkError(error) -> ErrorCategory.NETWORK
            isParsingError(error) -> ErrorCategory.UNSUPPORTED_FORMAT
            isDecodingError(error) -> ErrorCategory.DECODER
            isTimeout(error) -> ErrorCategory.TIMEOUT
            isBlockedStream(error) -> ErrorCategory.BLOCKED
            else -> ErrorCategory.UNKNOWN
        }
    }

    fun getUserMessage(error: PlaybackException): String {
        return when (getErrorCategory(error)) {
            ErrorCategory.NETWORK -> "Помилка мережі. Перевірте з'єднання."
            ErrorCategory.UNSUPPORTED_FORMAT -> "Пошкоджений потік. Перемикаю на резервний..."
            ErrorCategory.DECODER -> "Помилка декодування. Спробуйте іншу якість."
            ErrorCategory.TIMEOUT -> "Час очікування вичерпано. Спробуйте ще раз."
            ErrorCategory.BLOCKED -> "Доступ до відео обмежено."
             ErrorCategory.UNKNOWN -> "Помилка відтворення: ${error.errorCodeName}"
        }
    }

    enum class ErrorCategory {
        NETWORK, UNSUPPORTED_FORMAT, DECODER, TIMEOUT, BLOCKED, UNKNOWN
    }
}
