package edu.csuci.tweeter.notification

import android.content.Context
import androidx.work.WorkerParameters

/** Runs a [RareAnimalCheck] every so often; see [PostCheckWorker]. */
class RareAnimalCheckWorker(
    context: Context,
    parameters: WorkerParameters,
    check: RareAnimalCheck
) : PostCheckWorker(context, parameters, check, NAME) {
    /** The name the check is scheduled under. */
    companion object {
        /** The check's work name, also its name in [LastCheckStore] and [NotifiedPostStore]. */
        const val NAME = "rareAnimalCheck"
    }
}
