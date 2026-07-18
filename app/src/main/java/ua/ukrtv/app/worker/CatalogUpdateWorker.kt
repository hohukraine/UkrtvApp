package ua.ukrtv.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ua.ukrtv.app.data.repository.CatalogRepository
import ua.ukrtv.app.util.AppLogger

@HiltWorker
class CatalogUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val catalogRepository: CatalogRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppLogger.i("CatalogUpdateWorker", "Starting periodic catalog update...")
        return try {
            catalogRepository.updateCatalogSuspend()
            AppLogger.i("CatalogUpdateWorker", "Catalog update completed successfully")
            Result.success()
        } catch (e: Exception) {
            AppLogger.w("CatalogUpdateWorker", "Catalog update failed: ${e.message}")
            if (runAttemptCount >= 3) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }
}
