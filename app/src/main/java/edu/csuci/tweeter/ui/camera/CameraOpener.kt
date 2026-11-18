package edu.csuci.tweeter.ui.camera

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Opens the camera app whenever [camera] asks for it with [CameraControls.cameraUriToOpen] while
 * this is composed. Requests the CAMERA permission first when needed, treating denial as cancelling
 * the picture, and tells [camera] whether it opened and, once it closes, whether it saved the
 * picture. That answer also arrives after Android stopped the app while the camera app was open.
 * Leaving composition gives up on a picture the camera app hasn't been opened for yet.
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

    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val uri = pendingUri
        pendingUri = null
        if ((isGranted && uri != null)) {
            cameraLauncher.openCamera(uri, currentCamera)
        } else {
            currentCamera.pictureCancelled()
        }
    }

    val uriToOpen = camera.cameraUriToOpen

    LaunchedEffect(uriToOpen) {
        uriToOpen?.let { uri ->
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                cameraLauncher.openCamera(uri, currentCamera)
            } else {
                pendingUri = uri
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
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
    } catch (error: SecurityException) {
        // Thrown when the app declares CAMERA but the user hasn't granted it.
        Log.e("CameraOpener", "Camera permission not granted", error)
        camera.cameraUnavailable()
    }
}
