package edu.csuci.tweeter.ui.camera

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Opens the camera app whenever [camera] asks for it with [CameraControls.cameraUriToOpen] while
 * this is composed, and tells [camera] whether it opened and, once it closes, whether it saved
 * the picture. That answer also arrives after Android stopped the app while the camera app was
 * open. Leaving composition gives up on a picture the camera app hasn't been opened for yet.
 */
@Composable
fun CameraOpener(camera: CameraControls) {
    val currentCamera by rememberUpdatedState(camera)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { isSaved ->
        if (isSaved) {
            currentCamera.pictureSaved()
        } else {
            currentCamera.pictureCancelled()
        }
    }
    val uriToOpen = camera.cameraUriToOpen

    LaunchedEffect(uriToOpen) {
        uriToOpen?.let { uri -> cameraLauncher.openCamera(uri, currentCamera) }
    }
    DisposableEffect(camera) {
        onDispose { camera.stopOpeningCamera() }
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
