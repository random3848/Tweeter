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
import kotlinx.coroutines.CoroutineStart
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
    private var phase by mutableStateOf(restoredPhase())

    override val cameraUriToOpen: String?
        get() = (phase as? Phase.ReadyToOpen)?.file?.contentUri

    override val isOpeningCamera: Boolean
        get() = phase is Phase.CreatingFile || phase is Phase.ReadyToOpen

    override val cameraFailed: Boolean
        get() = phase == Phase.Failed

    override fun takePicture() {
        if (isOpeningCamera) {
            return
        }

        val creation = scope.launch(start = CoroutineStart.LAZY) { createFileToOpen() }
        giveUpOnPicture(Phase.CreatingFile(creation))
        creation.start()
    }

    private suspend fun createFileToOpen() {
        try {
            updatePhase(Phase.ReadyToOpen(cameraFileStore.create()))
        } catch (error: IOException) {
            Log.e("CameraCapture", "Could not create a file for the camera photo", error)
            updatePhase(Phase.Failed)
        }
    }

    override fun cameraOpened() {
        val ready = phase as? Phase.ReadyToOpen ?: return
        updatePhase(Phase.CameraOpen(ready.file))
    }

    override fun cameraUnavailable() {
        giveUpOnPicture(Phase.Failed)
    }

    override fun stopOpeningCamera() {
        if (isOpeningCamera) {
            giveUpOnPicture(Phase.Idle)
        }
    }

    override fun pictureSaved() {
        val open = phase as? Phase.CameraOpen ?: return
        updatePhase(Phase.Idle)
        photo.chooseCameraPicture(open.file)
    }

    override fun pictureCancelled() {
        when (phase) {
            Phase.Idle, Phase.Failed -> Unit

            is Phase.CreatingFile, is Phase.ReadyToOpen, is Phase.CameraOpen -> {
                giveUpOnPicture(Phase.Idle)
            }
        }
    }

    override fun dismissCameraFailure() {
        if (cameraFailed) {
            updatePhase(Phase.Idle)
        }
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
        stopOpeningCamera()
    }

    // Stops taking the picture under way, if any, deleting its file, and moves on to [nextPhase].
    private fun giveUpOnPicture(nextPhase: Phase) {
        when (val abandoned = phase) {
            Phase.Idle, Phase.Failed -> Unit
            is Phase.CreatingFile -> abandoned.creation.cancel()
            is Phase.ReadyToOpen -> cameraFileStore.discard(abandoned.file.path)
            is Phase.CameraOpen -> cameraFileStore.discard(abandoned.file.path)
        }
        updatePhase(nextPhase)
    }

    // Only the camera-open file and the failure are saved: a file the camera app wasn't opened
    // with yet is given up on instead, and so is one being created.
    private fun updatePhase(newPhase: Phase) {
        phase = newPhase
        val openedFile = (newPhase as? Phase.CameraOpen)?.file
        savedStateHandle[OPENED_FILE_PATH_KEY] = openedFile?.path
        savedStateHandle[OPENED_FILE_URI_KEY] = openedFile?.contentUri
        savedStateHandle[FAILED_KEY] = newPhase == Phase.Failed
    }

    private fun restoredPhase(): Phase {
        val openedFile = savedOpenedFile()
        return when {
            openedFile != null -> Phase.CameraOpen(openedFile)
            savedStateHandle.get<Boolean>(FAILED_KEY) == true -> Phase.Failed
            else -> Phase.Idle
        }
    }

    private fun savedOpenedFile(): CameraFile? {
        val path = savedStateHandle.get<String>(OPENED_FILE_PATH_KEY) ?: return null
        val contentUri = savedStateHandle.get<String>(OPENED_FILE_URI_KEY) ?: return null
        return CameraFile(path = path, contentUri = contentUri)
    }

    // Where taking a picture is. Each phase holds what giving up on the picture has to undo.
    private sealed interface Phase {
        // No picture is being taken.
        data object Idle : Phase

        // The latest picture couldn't be taken: its file couldn't be created, or no camera app
        // could open.
        data object Failed : Phase

        // The file for the picture is being created by [creation].
        data class CreatingFile(val creation: Job) : Phase

        // The file is ready, for the screen to open the camera app with.
        data class ReadyToOpen(val file: CameraFile) : Phase

        // The camera app was opened with [file], and hasn't answered yet.
        data class CameraOpen(val file: CameraFile) : Phase
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
