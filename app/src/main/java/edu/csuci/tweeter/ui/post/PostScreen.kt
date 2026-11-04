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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.location.LocationFix
import edu.csuci.tweeter.location.LocationSource
import edu.csuci.tweeter.ui.auth.LogInPrompt
import edu.csuci.tweeter.ui.camera.CameraOpener
import edu.csuci.tweeter.ui.location.LocationPicker
import edu.csuci.tweeter.ui.navigation.TweeterTopBar
import kotlin.math.roundToInt

/**
 * Takes a photo or picks one from the camera roll, finds where it was taken, and lets the user
 * move that location on a map and share the photo as a post, under the app's top bar. Guests can
 * only browse, so while nobody is logged in it asks the user to log in instead.
 *
 * @param onLogIn Opens the login form.
 * @param onCreateAccount Opens the registration form.
 * @param modifier Applied to the whole screen.
 * @param postViewModel Shares the post and says whether anyone is logged in.
 */
@Composable
fun PostScreen(
    onLogIn: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    postViewModel: PostViewModel = viewModel(factory = PostViewModel.factory)
) {
    val loggedInUid by postViewModel.loggedInUid.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TweeterTopBar()
        if (loggedInUid == null) {
            LogInPrompt(
                title = stringResource(R.string.post_log_in_title),
                onLogIn = onLogIn,
                onCreateAccount = onCreateAccount,
                modifier = Modifier.weight(1f)
            )
        } else {
            PostForm(postViewModel = postViewModel, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PostForm(postViewModel: PostViewModel, modifier: Modifier = Modifier) {
    var pickerFailed by rememberSaveable { mutableStateOf(false) }
    var showPostDialog by rememberSaveable { mutableStateOf(false) }
    val photoUri = postViewModel.photoUri

    // Whatever the user answers, the photo is then located with what the app may read.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        postViewModel.photoUri?.let { uri ->
            postViewModel.locatePhoto(Uri.parse(uri), postViewModel.isPhotoFromCamera)
        }
    }

    // A new photo's location: where the device is for one just taken, the photo's own GPS tags
    // for one from the camera roll, which Android hides without ACCESS_MEDIA_LOCATION.
    fun locateNewPhoto() {
        locationPermissionLauncher.launch(
            if (postViewModel.isPhotoFromCamera) {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        )
    }

    CameraOpener(
        uriToOpen = postViewModel.cameraUriToOpen,
        onOpened = postViewModel::cameraOpened,
        onUnavailable = postViewModel::cameraUnavailable,
        onResult = { isSaved ->
            postViewModel.pictureTaken(isSaved)
            if (isSaved) {
                locateNewPhoto()
            }
        }
    )

    // The Files picker, not the Photo Picker: the Photo Picker strips a photo's GPS tags, which
    // the post's location is read from.
    val cameraRollLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            postViewModel.choosePhoto(uri.toString())
            locateNewPhoto()
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
            enabled = !postViewModel.isUploading && !postViewModel.isTakingPicture,
            onClick = {
                pickerFailed = false
                postViewModel.takePicture()
            }
        ) {
            Text(stringResource(R.string.take_picture))
        }

        Button(
            enabled = !postViewModel.isUploading && !postViewModel.isTakingPicture,
            onClick = {
                postViewModel.dismissCameraFailure()
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
                !postViewModel.isTakingPicture &&
                postViewModel.location != null &&
                postViewModel.uploadedPhotoUrl == null,
            onClick = { showPostDialog = true }
        ) {
            Text(stringResource(R.string.upload_photo))
        }
        if (showPostDialog) {
            PostDetailsDialog(
                onDismiss = { showPostDialog = false },
                onPost = { caption, animalType, confidence ->
                    showPostDialog = false
                    photoUri?.let { uri ->
                        postViewModel.uploadPhoto(Uri.parse(uri), caption, animalType, confidence)
                    }
                }
            )
        }

        if (postViewModel.isUploading) {
            CircularProgressIndicator()
        }

        val statusResource = when {
            postViewModel.isUploading -> {
                R.string.photo_uploading
            }

            postViewModel.cameraFailed -> {
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

@Composable
private fun PostDetailsDialog(onDismiss: () -> Unit, onPost: (String, String, Int) -> Unit) {
    var caption by rememberSaveable { mutableStateOf("") }
    var animalType by rememberSaveable { mutableStateOf("") }
    var confidence by rememberSaveable { mutableStateOf(3f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") }
                )
                OutlinedTextField(
                    value = animalType,
                    onValueChange = { animalType = it },
                    label = { Text("Animal type") }
                )
                Text("Confidence: ${confidence.roundToInt()} of 5")
                Slider(
                    value = confidence,
                    onValueChange = { confidence = it },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { onPost(caption, animalType, confidence.roundToInt()) }) {
                Text("Post")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
