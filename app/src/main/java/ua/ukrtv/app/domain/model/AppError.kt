package ua.ukrtv.app.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.parcelize.Parcelize

@Stable
@Immutable
@Parcelize
sealed class AppError(val message: String) : Parcelable {
    @Parcelize
    class NetworkError(private val _message: String) : AppError(_message)
    @Parcelize
    class ParsingError(private val _message: String) : AppError(_message)
    @Parcelize
    class ProviderBlockedError(val providerName: String, private val _message: String) : AppError(_message)
    @Parcelize
    class StreamNotFoundError(private val _message: String) : AppError(_message)
    @Parcelize
    class CodecFailureError(private val _message: String) : AppError(_message)
    @Parcelize
    class UnknownError(private val _message: String) : AppError(_message)
}
