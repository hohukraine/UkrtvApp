package ua.ukrtv.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ua.ukrtv.app.data.repository.SeriesIndexRepository
import ua.ukrtv.app.util.AppLogger

@HiltWorker
class SeriesIndexUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val seriesIndexRepository: SeriesIndexRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppLogger.i("SeriesIndexUpdateWorker", "Starting series index refresh from sitemap...")
        return try {
            val ok = seriesIndexRepository.refreshFromSitemap()
            if (ok) {
                AppLogger.i("SeriesIndexUpdateWorker", "Series index refresh completed")
                Result.success()
            } else {
                AppLogger.w("SeriesIndexUpdateWorker", "Series index refresh produced no data")
                Result.retry()
            }
        } catch (e: Exception) {
            AppLogger.w("SeriesIndexUpdateWorker", "Series index refresh failed: ${e.message}")
            if (runAttemptCount >= 3) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }
}
