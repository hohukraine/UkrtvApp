package ua.ukrtv.app.domain.model

sealed class AppError(val message: String, val cause: Throwable? = null) {
    class NetworkError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class ParsingError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class ProviderBlockedError(val providerName: String, message: String) : AppError(message)
    class StreamNotFoundError(message: String) : AppError(message)
    class CodecFailureError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class UnknownError(message: String, cause: Throwable? = null) : AppError(message, cause)
}
