package edu.csuci.tweeter.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import edu.csuci.tweeter.data.PostLookupException

private const val TAG = "PostCheckWorker"

/**
 * Runs [check] when WorkManager says to, also while the app is closed; see [schedulePostCheck].
 * A check whose posts can't be looked up is tried again later.
 *
 * Each kind of check has its own subclass, which WorkManager schedules and
 * [PostCheckWorkerFactory] creates by its class name.
 *
 * @param checkName The unique work name the check is scheduled under, which failures are logged
 *     with.
 */
abstract class PostCheckWorker(
    context: Context,
    parameters: WorkerParameters,
    private val check: PostCheck,
    private val checkName: String
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        check.run()
        Result.success()
    } catch (error: PostLookupException) {
        Log.w(TAG, "Check $checkName failed; trying again later", error)
        Result.retry()
    }
}
