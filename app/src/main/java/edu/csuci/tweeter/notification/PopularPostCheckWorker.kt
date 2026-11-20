package edu.csuci.tweeter.notification

import android.content.Context
import androidx.work.WorkerParameters

/** Runs a [PopularPostCheck] every so often; see [PostCheckWorker]. */
class PopularPostCheckWorker(
    context: Context,
    parameters: WorkerParameters,
    check: PopularPostCheck
) : PostCheckWorker(context, parameters, check, NAME) {
    /** The name the check is scheduled under, and how often it runs. */
    companion object {
        /**
         * The unique work name of the check, also the name of its [NotifiedPostStore], and,
         * followed by a dot and a user's ID, of the [LastCheckStore] of when it last ran for them.
         */
        const val NAME = "popularPostCheck"

        /**
         * How often the check runs. Each run reads every popular post, not only new ones as the
         * rare-animal check does, so it runs hourly rather than every 15 minutes: a post stays
         * popular news for days, and an hour's delay costs a quarter of the reads.
         */
        const val INTERVAL_MINUTES = 60L
    }
}
