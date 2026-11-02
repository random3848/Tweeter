package edu.csuci.tweeter.ui.post

import android.Manifest
import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.location.LocationFix
import edu.csuci.tweeter.location.LocationSource
import edu.csuci.tweeter.ui.location.LocationPicker
import java.io.File
import java.io.IOException

/** Deletes a temporary camera file and logs unsuccessful cleanup. */
private fun deleteCachedPhoto(path: String?) {
    if (path == null) {
        return
    }

    val file = File(path)
    if (file.exists() && !file.delete()) {
        Log.w("PostScreen", "Could not delete cached photo")
    }
}

/**
 * Takes a photo or picks one from the camera roll, finds where it was taken, and lets the user
 * move that location on a map and share the photo as a post.
 */
@Composable
fun PostScreen(
    modifier: Modifier = Modifier,
    postViewModel: PostViewModel = viewModel(factory = PostViewModel.factory)
) {
    val context = LocalContext.current

    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraFailed by rememberSaveable { mutableStateOf(false) }
    var pickerFailed by rememberSaveable { mutableStateOf(false) }
    var isFromCamera by rememberSaveable { mutableStateOf(false) }

    // Whatever the user answers, the photo is then located with what the app may read.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        photoUri?.let { uri ->
            postViewModel.locatePhoto(Uri.parse(uri), capturedInApp = isFromCamera)
        }
    }

    // A new photo's location: where the device is for one just taken, the photo's own GPS tags
    // for one from the camera roll, which Android hides without ACCESS_MEDIA_LOCATION.
    fun locateNewPhoto() {
        locationPermissionLauncher.launch(
            if (isFromCamera) {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingUri != null) {
            deleteCachedPhoto(photoPath)
            postViewModel.clearUploadResult()
            photoUri = pendingUri
            photoPath = pendingPath
            isFromCamera = true
            locateNewPhoto()
        } else {
            deleteCachedPhoto(pendingPath)
        }

        pendingUri = null
        pendingPath = null
    }

    // The Files picker, not the Photo Picker: the Photo Picker strips a photo's GPS tags, which
    // the post's location is read from.
    val cameraRollLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            deleteCachedPhoto(photoPath)
            postViewModel.clearUploadResult()
            photoUri = uri.toString()
            photoPath = null
            isFromCamera = false
            locateNewPhoto()
        }
    }

    LaunchedEffect(postViewModel.uploadedPhotoUrl) {
        if (postViewModel.uploadedPhotoUrl != null) {
            deleteCachedPhoto(photoPath)
            photoPath = null
            photoUri = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(
            16.dp,
            Alignment.CenterVertically
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            enabled = !postViewModel.isUploading && pendingPath == null,
            onClick = {
                cameraFailed = false
                pickerFailed = false

                try {
                    val directory = File(context.cacheDir, "photos")
                    if (!directory.isDirectory && !directory.mkdirs()) {
                        throw IOException("Could not create photo directory")
                    }

                    val file = File.createTempFile("photo_", ".jpg", directory)
                    pendingPath = file.absolutePath

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )

                    pendingUri = uri.toString()
                    cameraLauncher.launch(uri)
                } catch (error: IOException) {
                    Log.e("PostScreen", "Could not create camera photo", error)
                    cameraFailed = true
                } catch (error: ActivityNotFoundException) {
                    Log.e("PostScreen", "No camera application available", error)
                    cameraFailed = true
                }

                if (cameraFailed) {
                    deleteCachedPhoto(pendingPath)
                    pendingPath = null
                    pendingUri = null
                }
            }
        ) {
            Text(stringResource(R.string.take_picture))
        }

        Button(
            enabled = !postViewModel.isUploading && pendingPath == null,
            onClick = {
                cameraFailed = false
                pickerFailed = false

                try {
                    cameraRollLauncher.launch(arrayOf("image/*"))
                } catch (error: ActivityNotFoundException) {
                    Log.e("PostScreen", "No file picker available", error)
                    pickerFailed = true
                }
            }
        ) {
            Text(stringResource(R.string.choose_from_camera_roll))
        }

        Button(
            enabled = photoUri != null &&
                !postViewModel.isUploading &&
                pendingPath == null &&
                postViewModel.location != null &&
                postViewModel.uploadedPhotoUrl == null,
            onClick = {
                photoUri?.let { uri ->
                    postViewModel.uploadPhoto(Uri.parse(uri))
                }
            }
        ) {
            Text(stringResource(R.string.upload_photo))
        }

        if (postViewModel.isUploading) {
            CircularProgressIndicator()
        }

        val statusResource = when {
            postViewModel.isUploading -> {
                R.string.photo_uploading
            }

            cameraFailed -> {
                R.string.photo_camera_failed
            }

            pickerFailed -> {
                R.string.photo_picker_failed
            }

            postViewModel.uploadFailed -> {
                R.string.photo_upload_failed
            }

            postViewModel.uploadedPhotoUrl != null -> {
                R.string.photo_uploaded
            }

            photoUri != null -> {
                R.string.photo_ready
            }

            else -> {
                R.string.photo_not_taken
            }
        }

        Text(stringResource(statusResource))

        if (photoUri != null) {
            Text(locationStatus(postViewModel.location, postViewModel.isLocating))
            LocationPicker(
                selected = postViewModel.location?.point,
                onSelect = postViewModel::pickLocation,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

/** What the post screen says about the photo's [location] while or after [isLocating]. */
@Composable
private fun locationStatus(location: LocationFix?, isLocating: Boolean): String = when {
    isLocating -> stringResource(R.string.post_location_finding)

    location == null -> stringResource(R.string.post_location_needed)

    else -> {
        val text = when (location.source) {
            LocationSource.EXIF -> R.string.post_location_from_photo
            LocationSource.DEVICE -> R.string.post_location_from_device
            LocationSource.MANUAL -> R.string.post_location_picked
        }
        stringResource(text, location.point.latitude, location.point.longitude)
    }
}
