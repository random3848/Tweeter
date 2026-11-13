package edu.csuci.tweeter.ui.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.CameraFile
import edu.csuci.tweeter.data.CameraFileStore

private const val URI_KEY = "chosenPhotoUri"
private const val CAMERA_FILE_KEY = "chosenPhotoCameraFile"

/**
 * The photo the user chose on a screen, for its ViewModel to use: one the camera app took,
 * through [CameraCapture], or one from the camera roll. It survives Android stopping the app,
 * through [savedStateHandle].
 *
 * A photo the camera app took is a file of the app's own, deleted once the photo is replaced or
 * cleared. One still chosen when its screen closes stays until the app next starts afresh, when
 * `CameraFileStore.discardAll` deletes it: until then, Android may restore the screen.
 *
 * While [lock]ed, as while the ViewModel uploads it, the photo isn't replaced: a new one is
 * dropped, and the file of one the camera app took deleted.
 *
 * @param cameraFileStore Deletes the files of photos the camera app took.
 */
class ChosenPhoto(
    private val cameraFileStore: CameraFileStore,
    private val savedStateHandle: SavedStateHandle
) {
    /** The photo's `content://` URI, or null until one is chosen, and again once it's cleared. */
    var uri by mutableStateOf(savedStateHandle.get<String>(URI_KEY))
        private set

    // The file of a photo the camera app took, or null for one from the camera roll.
    private var cameraFilePath by mutableStateOf(savedStateHandle.get<String>(CAMERA_FILE_KEY))

    private var isLocked = false

    /** Whether the camera app took the photo, rather than the user choosing it. */
    val isFromCamera: Boolean
        get() = cameraFilePath != null

    /** Makes the photo at [uri], from the camera roll, the photo, unless [lock]ed. */
    fun chooseFromCameraRoll(uri: String) {
        if (!isLocked) {
            replace(uri, newCameraFilePath = null)
        }
    }

    /**
     * Makes [picture], which the camera app saved, the photo, unless [lock]ed; then it deletes
     * [picture] instead.
     */
    fun chooseCameraPicture(picture: CameraFile) {
        if (isLocked) {
            cameraFileStore.discard(picture.path)
        } else {
            replace(picture.contentUri, picture.path)
        }
    }

    /** Leaves no photo chosen, such as once it's shared, also while [lock]ed. */
    fun clear() {
        replace(newUri = null, newCameraFilePath = null)
    }

    /** Keeps the photo from being replaced, while it's uploaded, until [unlock]. */
    fun lock() {
        isLocked = true
    }

    /** Lets the photo be replaced again, after [lock]. */
    fun unlock() {
        isLocked = false
    }

    // Replaces the photo, deleting the file of the one replaced if the camera app took it.
    private fun replace(newUri: String?, newCameraFilePath: String?) {
        val replacedCameraFilePath = cameraFilePath
        uri = newUri
        cameraFilePath = newCameraFilePath
        savedStateHandle[URI_KEY] = newUri
        savedStateHandle[CAMERA_FILE_KEY] = newCameraFilePath
        replacedCameraFilePath?.let(cameraFileStore::discard)
    }
}
