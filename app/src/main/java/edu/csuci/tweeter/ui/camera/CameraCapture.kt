package edu.csuci.tweeter.ui.camera

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.CameraFile
import edu.csuci.tweeter.data.CameraFileStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val OPENED_FILE_PATH_KEY = "cameraFilePath"
private const val OPENED_FILE_URI_KEY = "cameraFileUri"
private const val FAILED_KEY = "cameraFailed"

/**
 * A picture being taken with the camera app for a ViewModel, from creating the file the camera
 * app saves it into until the camera app answers, when the picture becomes [photo].
 *
 * The ViewModel exposes it as [CameraControls] and passes it to its `ViewModel` constructor, so
 * it's closed with the ViewModel. A file the camera app was opened with survives the app being
 * stopped meanwhile, through [savedStateHandle]; one it wasn't opened with yet doesn't, since the
 * screen restored later shouldn't open the camera app by itself.
 *
 * @param cameraFileStore Creates and deletes the file, off the main thread.
 * @param photo Where a picture the camera app saved goes.
 * @param savedStateHandle The ViewModel's, keeping the file the camera app was opened with.
 * @param scope Where the file is created, on the main thread. It's this capture's alone:
 *     [close] cancels it.
 */
class CameraCapture(
    private val cameraFileStore: CameraFileStore,
    private val photo: ChosenPhoto,
    private val savedStateHandle: SavedStateHandle,
    private val scope: CoroutineScope
) : CameraControls,
    AutoCloseable {
    private var isCreatingFile by mutableStateOf(false)

    private var creation: Job? = null

    // Created for the picture, until the screen opens the camera app with it.
    private var fileToOpen by mutableStateOf<CameraFile?>(null)

    // The camera app was opened with it, and hasn't answered yet.
    private var openedFile: CameraFile? = savedOpenedFile()

    override val cameraUriToOpen: String?
        get() = fileToOpen?.contentUri

    override val isOpeningCamera: Boolean
        get() = isCreatingFile || fileToOpen != null

    override var cameraFailed by mutableStateOf(savedStateHandle[FAILED_KEY] ?: false)
        private set

    override fun takePicture() {
        if (isOpeningCamera) {
            return
        }

        updateFailed(false)
        discardOpenedFile()
        isCreatingFile = true
        creation = scope.launch { createFileToOpen() }
    }

    private suspend fun createFileToOpen() {
        try {
            fileToOpen = cameraFileStore.create()
        } catch (error: IOException) {
            Log.e("CameraCapture", "Could not create a file for the camera photo", error)
            updateFailed(true)
        } finally {
            isCreatingFile = false
        }
    }

    override fun cameraOpened() {
        val opened = fileToOpen ?: return
        fileToOpen = null
        updateOpenedFile(opened)
    }

    override fun cameraUnavailable() {
        updateFailed(true)
        discardFileToOpen()
    }

    override fun stopOpeningCamera() {
        creation?.cancel()
        discardFileToOpen()
    }

    override fun pictureSaved() {
        val saved = openedFile ?: return
        updateOpenedFile(null)
        photo.chooseCameraPicture(saved)
    }

    override fun pictureCancelled() {
        discardOpenedFile()
    }

    override fun dismissCameraFailure() {
        updateFailed(false)
    }

    /**
     * Stops creating a file, for the ViewModel being cleared, and deletes one the camera app
     * wasn't opened with, which nothing can restore. The file the camera app was opened with
     * stays: the ViewModel may be cleared only for its activity to be created again, and the one
     * restored then still waits for the camera app's answer. `CameraFileStore.discardAll`
     * deletes it when the app next starts afresh.
     */
    override fun close() {
        scope.cancel()
        discardFileToOpen()
    }

    private fun discardFileToOpen() {
        val discarded = fileToOpen ?: return
        fileToOpen = null
        cameraFileStore.discard(discarded.path)
    }

    private fun discardOpenedFile() {
        val discarded = openedFile ?: return
        updateOpenedFile(null)
        cameraFileStore.discard(discarded.path)
    }

    private fun savedOpenedFile(): CameraFile? {
        val path = savedStateHandle.get<String>(OPENED_FILE_PATH_KEY) ?: return null
        val contentUri = savedStateHandle.get<String>(OPENED_FILE_URI_KEY) ?: return null
        return CameraFile(path = path, contentUri = contentUri)
    }

    private fun updateOpenedFile(newFile: CameraFile?) {
        openedFile = newFile
        savedStateHandle[OPENED_FILE_PATH_KEY] = newFile?.path
        savedStateHandle[OPENED_FILE_URI_KEY] = newFile?.contentUri
    }

    private fun updateFailed(hasFailed: Boolean) {
        cameraFailed = hasFailed
        savedStateHandle[FAILED_KEY] = hasFailed
    }

    /** Creates [CameraCapture]s. */
    companion object {
        /**
         * A capture that creates files from the main thread, in a scope of its own that is
         * cancelled when it's closed, as a ViewModel's `viewModelScope` is.
         */
        fun onMainThread(
            cameraFileStore: CameraFileStore,
            photo: ChosenPhoto,
            savedStateHandle: SavedStateHandle
        ): CameraCapture = CameraCapture(
            cameraFileStore = cameraFileStore,
            photo = photo,
            savedStateHandle = savedStateHandle,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        )
    }
}
