package edu.csuci.tweeter.data

import android.util.Log
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long [cleanUpUnusedPhoto] waits for a photo to be deleted, so a caller that is cleaning up
 * after a failure, or that was cancelled, isn't held up for long.
 */
internal const val PHOTO_CLEANUP_TIMEOUT_MILLIS = 5_000L

private const val TAG = "PhotoCleanup"

/**
 * Runs [delete], which deletes an uploaded photo that nothing uses, as far as it can: even when
 * the coroutine is cancelled, for at most [PHOTO_CLEANUP_TIMEOUT_MILLIS], and logging a
 * [PhotoDeletionException] instead of throwing it, so that callers still report the failure or
 * cancellation that left the photo unused.
 */
internal suspend fun cleanUpUnusedPhoto(delete: suspend () -> Unit) {
    withContext(NonCancellable) { deleteInTime(delete) }
}

private suspend fun deleteInTime(delete: suspend () -> Unit) {
    val finished = withTimeoutOrNull(PHOTO_CLEANUP_TIMEOUT_MILLIS) { deleteOrLogFailure(delete) }
    if (finished == null) {
        Log.w(TAG, "Gave up waiting for an unused photo to be deleted")
    }
}

private suspend fun deleteOrLogFailure(delete: suspend () -> Unit) {
    try {
        delete()
    } catch (error: PhotoDeletionException) {
        Log.w(TAG, "Could not delete an unused photo", error)
    }
}
