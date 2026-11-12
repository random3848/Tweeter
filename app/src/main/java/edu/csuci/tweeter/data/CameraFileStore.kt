package edu.csuci.tweeter.data

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Where camera photos are written, inside the app's cache directory. FileProvider shares this
// folder with the camera app (the "photos" path in res/xml/file_paths.xml).
private const val CAMERA_DIRECTORY = "photos"

/**
 * A file the camera app saves a picture into.
 *
 * @property path Where the file is on the phone, for deleting it with [CameraFileStore.discard].
 * @property contentUri The `content://` URI the camera app writes the picture through, which the
 *     app reads it back through too.
 */
data class CameraFile(val path: String, val contentUri: String)

/**
 * Creates the files the camera app saves pictures into, and deletes them again, on [ioDispatcher]
 * rather than the calling thread.
 *
 * @param findDirectory Returns where the files go, which is created when it's missing. Called on
 *     [ioDispatcher], since finding the directory can touch the disk.
 * @param scope Outlives the screens that use the files, so a file is deleted even when the screen
 *     that discarded it has closed.
 * @param contentUriOf Returns the URI that shares a file in the directory with the camera app.
 */
class CameraFileStore(
    private val findDirectory: () -> File,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val contentUriOf: (File) -> String
) {
    /**
     * Creates an empty file for the camera app to save a picture into. The caller discards it
     * with [discard] once nothing needs it. No file is left behind when this fails or is
     * cancelled.
     *
     * @throws IOException If the file can't be created.
     */
    suspend fun create(): CameraFile = prepareNewFile(
        dispatcher = ioDispatcher,
        create = { createEmptyPhotoFile(findDirectory()) },
        prepare = { file -> CameraFile(path = file.path, contentUri = contentUriOf(file)) }
    )

    /**
     * Deletes the camera file at [path], if it's still there, without waiting for it. Safe to
     * call from the main thread, and from a ViewModel being cleared.
     */
    fun discard(path: String) {
        scope.launch(ioDispatcher) { deleteFile(ioDispatcher, File(path)) }
    }

    /** Creates [CameraFileStore]s. */
    companion object {
        /**
         * Camera files in the cache directory of [context], shared with the camera app through
         * the app's FileProvider.
         */
        fun inCacheOf(
            context: Context,
            scope: CoroutineScope,
            ioDispatcher: CoroutineDispatcher
        ): CameraFileStore = CameraFileStore(
            findDirectory = { File(context.cacheDir, CAMERA_DIRECTORY) },
            scope = scope,
            ioDispatcher = ioDispatcher,
            contentUriOf = { file ->
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    .toString()
            }
        )
    }
}
