package edu.csuci.tweeter.location

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Creates a file with [create], fills it with [write], both on [dispatcher], and returns it.
 *
 * The file is deleted, also on [dispatcher], unless the caller gets it: when [write] fails, and
 * when the calling coroutine is cancelled, even after [write] finished. `withContext` throws on
 * return then, so deleting it inside the block wouldn't be enough.
 */
internal suspend fun writeNewFile(
    dispatcher: CoroutineDispatcher,
    create: () -> File,
    write: (File) -> Unit
): File {
    var file: File? = null
    try {
        return withContext(dispatcher) {
            create().also {
                file = it
                write(it)
            }
        }
    } catch (error: Throwable) {
        file?.let { deleteFile(dispatcher, it) }
        throw error
    }
}

/**
 * Deletes [file] on [dispatcher], even when the calling coroutine is cancelled, so a file that
 * is no longer needed doesn't stay behind because whatever used it was stopped.
 */
internal suspend fun deleteFile(dispatcher: CoroutineDispatcher, file: File) {
    withContext(NonCancellable + dispatcher) { file.delete() }
}
