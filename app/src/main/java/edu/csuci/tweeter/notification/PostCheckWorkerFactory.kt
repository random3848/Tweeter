package edu.csuci.tweeter.notification

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/**
 * Creates the workers of the checks for posts to notify the user of, giving each its check, so
 * that WorkManager doesn't create them by reflection. Each check is created only once a worker
 * needs it.
 *
 * @param rareAnimalCheck Gets the check [RareAnimalCheckWorker] runs.
 * @param popularPostCheck Gets the check [PopularPostCheckWorker] runs.
 */
class PostCheckWorkerFactory(
    private val rareAnimalCheck: () -> RareAnimalCheck,
    private val popularPostCheck: () -> PopularPostCheck
) : WorkerFactory() {
    // Null for any other worker, such as WorkManager's own, which it then creates itself.
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        RareAnimalCheckWorker::class.java.name -> {
            RareAnimalCheckWorker(appContext, workerParameters, rareAnimalCheck())
        }

        PopularPostCheckWorker::class.java.name -> {
            PopularPostCheckWorker(appContext, workerParameters, popularPostCheck())
        }

        else -> null
    }
}
