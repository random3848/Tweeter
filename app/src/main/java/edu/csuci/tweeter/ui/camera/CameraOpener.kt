package edu.csuci.tweeter.ui.camera

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Opens the camera app to save a picture into [uriToOpen] whenever that is set, as
 * [CameraCapture.uriToOpen] asks, and reports what happened.
 *
 * @param onOpened Called once the camera app is open, so it isn't opened again.
 * @param onUnavailable Called instead when no camera app could open.
 * @param onResult Called with whether the camera app saved a picture, once it closes. This also
 *     works after Android stopped the app while the camera app was open.
 */
@Composable
fun CameraOpener(
    uriToOpen: String?,
    onOpened: () -> Unit,
    onUnavailable: () -> Unit,
    onResult: (isSaved: Boolean) -> Unit
) {
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = onResult
    )
    val currentOnOpened by rememberUpdatedState(onOpened)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)

    LaunchedEffect(uriToOpen) {
        if (uriToOpen == null) {
            return@LaunchedEffect
        }

        try {
            cameraLauncher.launch(Uri.parse(uriToOpen))
            currentOnOpened()
        } catch (error: ActivityNotFoundException) {
            Log.e("CameraOpener", "No camera app", error)
            currentOnUnavailable()
        }
    }
}
