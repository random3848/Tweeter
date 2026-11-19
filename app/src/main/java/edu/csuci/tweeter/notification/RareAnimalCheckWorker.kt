package edu.csuci.tweeter.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import edu.csuci.tweeter.data.PostLookupException

/**
 * Runs [check] when WorkManager says to, also while the app is closed; see [schedulePostCheck].
 * A check whose posts can't be looked up is tried again later.
 */
class RareAnimalCheckWorker(
    context: Context,
    parameters: WorkerParameters,
    private val check: RareAnimalCheck
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        check.run()
        Result.success()
    } catch (error: PostLookupException) {
        Log.w(TAG, "Checking for rare animals failed; trying again later", error)
        Result.retry()
    }

    /** The name the check is scheduled under. */
    companion object {
        /** The check's work name, also its name in [LastCheckStore] and [NotifiedPostStore]. */
        const val NAME = "rareAnimalCheck"

        private const val TAG = "RareAnimalCheck"
    }
}
