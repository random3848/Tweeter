package edu.csuci.tweeter.data

import android.content.Context
import androidx.core.content.FileProvider
import edu.csuci.tweeter.location.prepareNewFile
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
 * @property path Where the file is on the phone, for deleting it with [CameraFiles.discard].
 * @property contentUri The `content://` URI the camera app writes the picture through, which the
 *     app reads it back through too.
 */
data class CameraFile(val path: String, val contentUri: String)

/**
 * Creates the files the camera app saves pictures into, and deletes them again, on [ioDispatcher]
 * rather than the calling thread.
 *
 * @param directory Where the files go; created when it's missing.
 * @param scope Outlives the screens that use the files, so a file is deleted even when the screen
 *     that discarded it has closed.
 * @param contentUriOf Returns the URI that shares a file in [directory] with the camera app.
 */
class CameraFiles(
    private val directory: File,
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
    suspend fun create(): CameraFile = prepareNewFile(ioDispatcher, ::createEmptyFile) { file ->
        CameraFile(path = file.path, contentUri = contentUriOf(file))
    }

    /**
     * Deletes the camera file at [path], if it's still there, without waiting for it. Safe to
     * call from the main thread, and from a ViewModel being cleared.
     */
    fun discard(path: String) {
        scope.launch(ioDispatcher) { File(path).delete() }
    }

    private fun createEmptyFile(): File {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create $directory")
        }
        return File.createTempFile("photo_", ".jpg", directory)
    }

    /** Creates [CameraFiles]. */
    companion object {
        /**
         * Camera files in the cache directory of [context], shared with the camera app through
         * the app's FileProvider.
         */
        fun inCacheOf(
            context: Context,
            scope: CoroutineScope,
            ioDispatcher: CoroutineDispatcher
        ): CameraFiles = CameraFiles(
            directory = File(context.cacheDir, CAMERA_DIRECTORY),
            scope = scope,
            ioDispatcher = ioDispatcher,
            contentUriOf = { file ->
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    .toString()
            }
        )
    }
}
