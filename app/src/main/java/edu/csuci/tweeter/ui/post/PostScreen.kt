package edu.csuci.tweeter.ui.post

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.location.LocationFix
import edu.csuci.tweeter.location.LocationSource
import edu.csuci.tweeter.ui.auth.LogInPrompt
import edu.csuci.tweeter.ui.location.LocationPicker
import edu.csuci.tweeter.ui.navigation.TweeterTopBar
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Takes a photo with the live camera or picks one from the camera roll, finds where it was taken,
 * and lets the user move that location on a map and share the photo as a post, under the app's
 * top bar. Guests can only browse, so while nobody is logged in it asks the user to log in
 * instead.
 *
 * @param onLogIn Opens the login form.
 * @param onCreateAccount Opens the registration form.
 * @param onClose Leaves the camera screen.
 * @param modifier Applied to the whole screen.
 * @param postViewModel Shares the post and says whether anyone is logged in.
 */
@Composable
fun PostScreen(
    onLogIn: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
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
            PostForm(
                postViewModel = postViewModel,
                onClose = onClose,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PostForm(
    postViewModel: PostViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var captureFailed by rememberSaveable { mutableStateOf(false) }
    var pickerFailed by rememberSaveable { mutableStateOf(false) }
    var showPostDialog by rememberSaveable { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    val photoUri = postViewModel.photoUri

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val imageCapture = remember { ImageCapture.Builder().build() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        postViewModel.locatePhoto()
    }

    // A new photo's location: where the device is for one just taken, the photo's own GPS tags
    // for one from the camera roll, which Android hides without ACCESS_MEDIA_LOCATION. Asked
    // for once per photo.
    val photoToLocate = postViewModel.photoToLocate
    LaunchedEffect(photoToLocate) {
        if (photoToLocate == null) {
            return@LaunchedEffect
        }

        postViewModel.locationPermissionsRequested()
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

    // The Files picker, not the Photo Picker: the Photo Picker strips a photo's GPS tags, which
    // the post's location is read from.
    val cameraRollLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            postViewModel.choosePhoto(uri.toString())
        }
    }

    // The file is created off the main thread, then CameraX saves the picture into it.
    fun takePhoto() {
        if (isCapturing) {
            return
        }

        isCapturing = true
        captureFailed = false
        pickerFailed = false
        scope.launch {
            val file = try {
                postViewModel.createCameraFile()
            } catch (error: IOException) {
                Log.e("PostScreen", "Could not create camera photo file", error)
                captureFailed = true
                isCapturing = false
                return@launch
            }

            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(File(file.path)).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(
                        outputFileResults: ImageCapture.OutputFileResults
                    ) {
                        postViewModel.cameraPictureCaptured(file)
                        isCapturing = false
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("PostScreen", "Camera capture failed", exception)
                        postViewModel.discardCameraFile(file)
                        captureFailed = true
                        isCapturing = false
                    }
                }
            )
        }
    }

    if (photoUri == null) {
        Box(modifier = modifier.fillMaxSize()) {
            if (hasCameraPermission) {
                CameraPreview(
                    imageCapture = imageCapture,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.camera_permission_required))
                    Button(
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(stringResource(R.string.grant_permission))
                    }
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.camera_close),
                    tint = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (captureFailed) {
                    Text(stringResource(R.string.photo_camera_failed), color = Color.White)
                }
                if (pickerFailed) {
                    Text(stringResource(R.string.photo_picker_failed), color = Color.White)
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = {
                            captureFailed = false
                            pickerFailed = false
                            try {
                                cameraRollLauncher.launch(arrayOf("image/*"))
                            } catch (error: ActivityNotFoundException) {
                                Log.e("PostScreen", "No file picker available", error)
                                pickerFailed = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.4f),
                        border = BorderStroke(2.dp, Color.White),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_camera_filled),
                                contentDescription =
                                    stringResource(R.string.choose_from_camera_roll),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(
                                enabled = hasCameraPermission &&
                                    !isCapturing &&
                                    !postViewModel.isUploading
                            ) { takePhoto() }
                    )
                }
            }
        }
    } else {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Button(
                    enabled = !postViewModel.isUploading,
                    onClick = {
                        captureFailed = false
                        pickerFailed = false
                        postViewModel.retakePhoto()
                    }
                ) {
                    Text(stringResource(R.string.camera_retake))
                }
            }

            Button(
                enabled = !postViewModel.isUploading && postViewModel.location != null,
                onClick = { showPostDialog = true }
            ) {
                Text(stringResource(R.string.upload_photo))
            }
            if (showPostDialog) {
                PostDetailsDialog(
                    onDismiss = { showPostDialog = false },
                    onPost = { caption, animalType, confidence ->
                        showPostDialog = false
                        postViewModel.uploadPhoto(caption, animalType, confidence)
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

                postViewModel.uploadFailed -> {
                    R.string.photo_upload_failed
                }

                else -> {
                    R.string.photo_ready
                }
            }
            Text(stringResource(statusResource))

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

// The live camera view, which also takes the picture for [imageCapture]. It's bound once, when
// the view is created, and unbinds when the screen's lifecycle ends.
@Composable
private fun CameraPreview(imageCapture: ImageCapture, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = surfaceProvider
                    }
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    } catch (error: Exception) {
                        Log.e("PostScreen", "CameraX use case binding failed", error)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        },
        modifier = modifier
    )
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
private fun PostDetailsDialog(
    onDismiss: () -> Unit,
    onPost: (String, String, Int) -> Unit,
    animalPicker: AnimalPickerViewModel = viewModel(factory = AnimalPickerViewModel.factory)
) {
    var caption by rememberSaveable { mutableStateOf("") }
    var confidence by rememberSaveable { mutableStateOf(3f) }
    // The picker outlives the dialog, so it's emptied on close, as the caption is.
    val close = {
        animalPicker.clear()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("Post details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") }
                )
                AnimalField(picker = animalPicker)
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
            Button(
                enabled = animalPicker.isComplete,
                onClick = {
                    onPost(caption, animalPicker.chosenAnimal.orEmpty(), confidence.roundToInt())
                    animalPicker.clear()
                }
            ) {
                Text("Post")
            }
        },
        dismissButton = {
            TextButton(onClick = close) { Text("Cancel") }
        }
    )
}
