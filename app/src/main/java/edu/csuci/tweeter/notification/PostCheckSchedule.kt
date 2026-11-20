package edu.csuci.tweeter.notification

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

// How often a check for posts runs: the shortest period WorkManager allows.
private const val CHECK_INTERVAL_MINUTES = 15L

/**
 * Schedules [worker] to check for posts to notify the user of every 15 minutes while the device is
 * online, also while the app is closed, as the unique work [name]. Scheduling a check that is
 * already scheduled keeps its timing, so the app can schedule it every time it starts.
 */
fun WorkManager.schedulePostCheck(name: String, worker: Class<out ListenableWorker>) {
    val onlyOnline = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val request = PeriodicWorkRequest.Builder(worker, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
        .setConstraints(onlyOnline)
        .build()
    enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
}
