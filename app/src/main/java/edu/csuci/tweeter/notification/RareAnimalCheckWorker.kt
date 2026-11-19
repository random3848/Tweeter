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
        /** The unique work name of the check, also the name of its [LastCheckStore]. */
        const val NAME = "rareAnimalCheck"
    }
}
