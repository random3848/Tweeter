package edu.csuci.tweeter.location

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Creates a file with [create], fills it with [write], both on [dispatcher], and returns it.
 *
 * The file is deleted unless the caller gets it, as [prepareNewFile] describes.
 */
internal suspend fun writeNewFile(
    dispatcher: CoroutineDispatcher,
    create: () -> File,
    write: (File) -> Unit
): File = prepareNewFile(dispatcher, create) { file ->
    write(file)
    file
}

/**
 * Creates a file with [create], readies it for its user with [prepare], both on [dispatcher], and
 * returns what [prepare] returns.
 *
 * The file is deleted, also on [dispatcher], unless the caller gets the result: when [prepare]
 * fails, and when the calling coroutine is cancelled, even after [prepare] finished.
 * `withContext` throws on return then, so deleting it inside the block wouldn't be enough.
 */
internal suspend fun <ResultT> prepareNewFile(
    dispatcher: CoroutineDispatcher,
    create: () -> File,
    prepare: (File) -> ResultT
): ResultT {
    var file: File? = null
    try {
        return withContext(dispatcher) {
            val created = create()
            file = created
            prepare(created)
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
