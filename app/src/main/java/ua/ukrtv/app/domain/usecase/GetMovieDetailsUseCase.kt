package ua.ukrtv.app.domain.usecase

import kotlinx.coroutines.flow.Flow
import ua.ukrtv.app.domain.model.MovieDetail
import ua.ukrtv.app.domain.repository.MediaRepository
import javax.inject.Inject

class GetMovieDetailsUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    operator fun invoke(id: String, url: String): Flow<Result<MovieDetail>> = 
        repository.getDetails(id, url)
}
