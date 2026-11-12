package edu.csuci.tweeter.data

import java.io.File
import java.io.IOException
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
): ResultT = FileInPreparation(dispatcher).createAndPrepare(create, prepare)

// One file being created and readied on [dispatcher], remembered so that it can be deleted when
// its caller doesn't get it.
private class FileInPreparation(private val dispatcher: CoroutineDispatcher) {
    private var file: File? = null

    suspend fun <ResultT> createAndPrepare(
        create: () -> File,
        prepare: (File) -> ResultT
    ): ResultT {
        try {
            return withContext(dispatcher) { prepare(create().also { file = it }) }
        } catch (error: Throwable) {
            file?.let { deleteFile(dispatcher, it) }
            throw error
        }
    }
}

/**
 * Deletes [file] on [dispatcher], even when the calling coroutine is cancelled, so a file that
 * is no longer needed doesn't stay behind because whatever used it was stopped.
 */
internal suspend fun deleteFile(dispatcher: CoroutineDispatcher, file: File) {
    withContext(NonCancellable + dispatcher) { file.delete() }
}

/**
 * Creates an empty file for a photo in [directory], and [directory] itself if it's missing. Each
 * call creates a file with a new name. It blocks, so callers pass it as `create` to
 * [prepareNewFile] or [writeNewFile].
 *
 * @throws IOException If [directory] or the file can't be created.
 */
internal fun createEmptyPhotoFile(directory: File): File {
    if (!directory.isDirectory && !directory.mkdirs()) {
        throw IOException("Could not create $directory")
    }
    return File.createTempFile("photo_", ".jpg", directory)
}
