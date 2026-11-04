package edu.csuci.tweeter.ui.camera

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.CameraFile
import edu.csuci.tweeter.data.CameraFiles
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val FILE_PATH_KEY = "cameraFilePath"
private const val FILE_URI_KEY = "cameraFileUri"
private const val IS_OPENED_KEY = "isCameraOpened"
private const val FAILED_KEY = "cameraFailed"

/**
 * A picture being taken with the camera app for a ViewModel, from creating the file the camera
 * app saves it into until the camera app answers.
 *
 * The ViewModel exposes it as [CameraControls], passes it to its `ViewModel` constructor so it's
 * closed with the ViewModel, and finishes each picture with [keep] or [discard]. The file
 * survives the app being stopped while the camera app is open, through [savedStateHandle].
 *
 * @param cameraFiles Creates and deletes the file, off the main thread.
 * @param savedStateHandle The ViewModel's, keeping the file and whether the camera app opened.
 * @param scope Where the file is created, on the main thread. It's this capture's alone:
 *     [close] cancels it.
 */
class CameraCapture(
    private val cameraFiles: CameraFiles,
    private val savedStateHandle: SavedStateHandle,
    private val scope: CoroutineScope
) : CameraControls,
    AutoCloseable {
    private var isCreatingFile by mutableStateOf(false)

    // The file the picture is being taken into, from its creation until the camera app answers.
    private var file by mutableStateOf(savedFile())

    private var isCameraOpened by mutableStateOf(savedStateHandle[IS_OPENED_KEY] ?: false)

    override val cameraUriToOpen: String?
        get() = file?.contentUri?.takeUnless { isCameraOpened }

    override val isTakingPicture: Boolean
        get() = isCreatingFile || file != null

    override var cameraFailed by mutableStateOf(savedStateHandle[FAILED_KEY] ?: false)
        private set

    override fun takePicture() {
        if (isTakingPicture) {
            return
        }

        updateFailed(false)
        scope.launch { createFileWhileTaking() }
    }

    // Creates the file, counting as taking a picture meanwhile so a second start is ignored.
    private suspend fun createFileWhileTaking() {
        isCreatingFile = true
        try {
            createFile()
        } finally {
            isCreatingFile = false
        }
    }

    override fun cameraOpened() {
        isCameraOpened = true
        savedStateHandle[IS_OPENED_KEY] = true
    }

    /**
     * Finishes taking the picture, which the camera app saved, and returns its file, or null when
     * no picture was being taken. The caller discards the file with [CameraFiles.discard] once
     * nothing needs it.
     */
    fun keep(): CameraFile? = file.also { updateFile(null) }

    /** Finishes taking the picture, which the camera app didn't save, and discards its file. */
    fun discard() {
        val discarded = file ?: return
        updateFile(null)
        cameraFiles.discard(discarded.path)
    }

    override fun cameraUnavailable() {
        updateFailed(true)
        discard()
    }

    override fun dismissCameraFailure() {
        updateFailed(false)
    }

    /**
     * Stops taking the picture, for the ViewModel being cleared: stops creating its file, and
     * discards the file of a picture still being taken, which nothing can keep anymore.
     */
    override fun close() {
        scope.cancel()
        discard()
    }

    private suspend fun createFile() {
        try {
            updateFile(cameraFiles.create())
        } catch (error: IOException) {
            Log.e("CameraCapture", "Could not create a file for the camera photo", error)
            updateFailed(true)
        }
    }

    private fun savedFile(): CameraFile? {
        val path = savedStateHandle.get<String>(FILE_PATH_KEY) ?: return null
        val contentUri = savedStateHandle.get<String>(FILE_URI_KEY) ?: return null
        return CameraFile(path = path, contentUri = contentUri)
    }

    private fun updateFile(newFile: CameraFile?) {
        file = newFile
        isCameraOpened = false
        savedStateHandle[FILE_PATH_KEY] = newFile?.path
        savedStateHandle[FILE_URI_KEY] = newFile?.contentUri
        savedStateHandle[IS_OPENED_KEY] = false
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
            cameraFiles: CameraFiles,
            savedStateHandle: SavedStateHandle
        ): CameraCapture = CameraCapture(
            cameraFiles = cameraFiles,
            savedStateHandle = savedStateHandle,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        )
    }
}
