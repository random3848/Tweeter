package edu.csuci.tweeter.ui.camera

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Opens the camera app whenever [camera] asks for it with [CameraControls.cameraUriToOpen], and
 * tells [camera] whether it opened.
 *
 * @param onResult Called with whether the camera app saved a picture, once it closes. This also
 *     works after Android stopped the app while the camera app was open.
 */
@Composable
fun CameraOpener(camera: CameraControls, onResult: (isSaved: Boolean) -> Unit) {
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = onResult
    )
    val currentCamera by rememberUpdatedState(camera)
    val uriToOpen = camera.cameraUriToOpen

    LaunchedEffect(uriToOpen) {
        uriToOpen?.let { uri -> cameraLauncher.openCamera(uri, currentCamera) }
    }
}

// Opens the camera app to save a picture into [uri], and tells [camera] whether it opened.
private fun ActivityResultLauncher<Uri>.openCamera(uri: String, camera: CameraControls) {
    try {
        launch(Uri.parse(uri))
        camera.cameraOpened()
    } catch (error: ActivityNotFoundException) {
        Log.e("CameraOpener", "No camera app", error)
        camera.cameraUnavailable()
    }
}
