package ua.ukrtv.app.domain.usecase

import ua.ukrtv.app.domain.repository.MediaRepository
import ua.ukrtv.app.domain.model.StreamResolutionResult
import javax.inject.Inject

class GetStreamUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    suspend operator fun invoke(url: String): StreamResolutionResult? = repository.getStream(url)
}

