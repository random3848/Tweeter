package edu.csuci.tweeter.ui.camera

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.CameraFile
import edu.csuci.tweeter.data.CameraFiles
import java.io.IOException

private const val FILE_PATH_KEY = "cameraFilePath"
private const val FILE_URI_KEY = "cameraFileUri"
private const val IS_OPENED_KEY = "isCameraOpened"
private const val FAILED_KEY = "cameraFailed"

/**
 * A picture being taken with the camera app for a ViewModel, from creating the file the camera
 * app saves it into until the camera app answers.
 *
 * The ViewModel calls [start]; its screen then opens the camera app with [uriToOpen] (see
 * [CameraOpener]) and reports back. The file survives the app being stopped while the camera app
 * is open, through [savedStateHandle].
 *
 * @param cameraFiles Creates and deletes the file, off the main thread.
 * @param savedStateHandle The ViewModel's, keeping the file and whether the camera app opened.
 */
class CameraCapture(
    private val cameraFiles: CameraFiles,
    private val savedStateHandle: SavedStateHandle
) {
    private var isCreatingFile by mutableStateOf(false)

    // The file the picture is being taken into, from its creation until the camera app answers.
    private var file by mutableStateOf(savedFile())

    private var isCameraOpened by mutableStateOf(savedStateHandle[IS_OPENED_KEY] ?: false)

    /**
     * The URI of the file for the camera app to save the picture into, or null unless the screen
     * should open the camera app now. It calls [cameraOpened] once it has.
     */
    val uriToOpen: String?
        get() = file?.contentUri?.takeUnless { isCameraOpened }

    /** Whether a picture is being taken: from [start] until the camera app answers. */
    val isTakingPicture: Boolean
        get() = isCreatingFile || file != null

    /**
     * Whether the latest picture couldn't be taken, because its file couldn't be created or no
     * camera app could open.
     */
    var failed by mutableStateOf(savedStateHandle[FAILED_KEY] ?: false)
        private set

    /**
     * Creates the file for a new picture, then asks to open the camera app. Ignored while a
     * picture is being taken.
     */
    suspend fun start() {
        if (isTakingPicture) {
            return
        }

        updateFailed(false)
        createFileWhileTaking()
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

    /** Stops asking to open the camera app, which the screen has opened. */
    fun cameraOpened() {
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

    /** Gives up on the picture, since no camera app could open, and discards its file. */
    fun cameraUnavailable() {
        updateFailed(true)
        discard()
    }

    /** Stops reporting that the latest picture failed, once the user moves on. */
    fun dismissFailure() {
        updateFailed(false)
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
        failed = hasFailed
        savedStateHandle[FAILED_KEY] = hasFailed
    }
}
