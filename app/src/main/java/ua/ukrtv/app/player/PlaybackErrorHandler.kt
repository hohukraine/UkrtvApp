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

    fun isTimeout(error: PlaybackException): Boolean {
        val code = error.errorCode
        return code == PlaybackException.ERROR_CODE_TIMEOUT ||
            code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
    }

fun isDecodingError(error: PlaybackException): Boolean {
        val code = error.errorCode
        val msg = error.message ?: ""
        
        // 🚀 CRITICAL FIX: Treat "Unexpected start code prefix" as a DECODER error.
        // This is usually a parsing error (ERROR_CODE_PARSING_CONTAINER_FAILED which is generic)
        // or a hardware decoder failing to handle a malformed bitstream.
        // Marking it as DECODER error will trigger the SOFTWARE fallback in PlayerViewModel.
        val isBitstreamError = msg.contains("Unexpected start code prefix", ignoreCase = true) || 
                               msg.contains("Format exceeds selected codec's capabilities", ignoreCase = true) ||
                               code == PlaybackException.ERROR_CODE_DECODING_FAILED
                                
        return isBitstreamError ||
            code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            code == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
            code == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
            msg.contains("OMX.", ignoreCase = true) ||
            msg.contains("ACodec", ignoreCase = true) ||
            msg.contains("0xfffffff4", ignoreCase = true) ||
            error.cause is java.lang.IllegalStateException
    }

    fun isCodecCriticalError(error: PlaybackException): Boolean {
        val msg = error.message ?: ""
        if (msg.contains("0xfffffff4") || msg.contains("setPortMode failed") || msg.contains("Unexpected start code prefix")) {
            return true
        }

        var cause: Throwable? = error.cause
        while (cause != null) {
            val causeMsg = cause.message ?: ""
            if (causeMsg.contains("OMX.") || causeMsg.contains("ACodec") || causeMsg.contains("0xfffffff4")) {
                return true
            }
            if (cause is java.lang.IllegalStateException) {
                val stackTrace = cause.stackTrace
                if (stackTrace.any { it.className.contains("MediaCodec") || it.className.contains("ACodec") }) {
                    return true
                }
            }
            if (cause.javaClass.name.contains("MediaCodec") && cause.javaClass.name.contains("Exception")) {
                return true
            }
            cause = cause.cause
        }
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
        return isUnsupportedFormat(error) || isDecodingError(error)
    }

    fun shouldRetry(error: PlaybackException): Boolean {
        return isNetworkError(error) || isTimeout(error)
    }

    fun getErrorCategory(error: PlaybackException): ErrorCategory {
        return when {
            isNetworkError(error) -> ErrorCategory.NETWORK
            isUnsupportedFormat(error) -> ErrorCategory.UNSUPPORTED_FORMAT
            isDecodingError(error) -> ErrorCategory.DECODER
            isTimeout(error) -> ErrorCategory.TIMEOUT
            isBlockedStream(error) -> ErrorCategory.BLOCKED
            else -> ErrorCategory.UNKNOWN
        }
    }

    fun getUserMessage(error: PlaybackException): String {
        return when (getErrorCategory(error)) {
            ErrorCategory.NETWORK -> "Помилка мережі. Перевірте з'єднання."
            ErrorCategory.UNSUPPORTED_FORMAT -> "Непідтримуваний формат відео."
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
