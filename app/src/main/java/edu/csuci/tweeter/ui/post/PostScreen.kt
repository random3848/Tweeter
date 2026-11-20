package edu.csuci.tweeter.ui.post

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.location.LocationFix
import edu.csuci.tweeter.location.LocationSource
import edu.csuci.tweeter.ui.auth.LogInPrompt
import edu.csuci.tweeter.ui.location.LocationPicker
import edu.csuci.tweeter.ui.navigation.TweeterTopBar
import kotlin.math.roundToInt

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
    onClose: () -> Unit,
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
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { postViewModel.locatePhoto() }

    val photoToLocate = postViewModel.photoToLocate
    LaunchedEffect(photoToLocate) {
        if (photoToLocate != null) {
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
    }

    if (postViewModel.photoUri == null) {
        CameraCaptureScreen(
            postViewModel = postViewModel,
            onClose = onClose,
            modifier = modifier
        )
    } else {
        PostDetailsForm(
            postViewModel = postViewModel,
            modifier = modifier
        )
    }
}

@Composable
private fun CameraCaptureScreen(
    postViewModel: PostViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var pickerFailed by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasCameraPermission) {
                    permissionDenied = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            permissionDenied = false
        } else {
            val activity = context as? android.app.Activity
            val showRationale = activity != null &&
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA
                )
            permissionDenied = !showRationale
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val pendingFile = postViewModel.pendingCaptureFile
    LaunchedEffect(pendingFile) {
        val file = pendingFile ?: return@LaunchedEffect
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(java.io.File(file.path)).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    postViewModel.onPhotoCaptured(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("PostScreen", "Camera capture failed", exception)
                    postViewModel.onCaptureFailed(file)
                }
            }
        )
    }
    DisposableEffect(postViewModel) {
        onDispose { postViewModel.cancelPhotoCapture() }
    }

    val cameraRollLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) postViewModel.choosePhoto(uri.toString())
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreview(imageCapture = imageCapture, modifier = Modifier.fillMaxSize())
        } else {
            CameraPermissionRequest(
                permissionDenied = permissionDenied,
                onRequestPermission = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                }
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(40.dp)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.camera_close),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        CameraBottomControls(
            captureFailed = postViewModel.captureFailed,
            pickerFailed = pickerFailed,
            showUploadedMessage = postViewModel.uploadedPhotoUrl != null,
            shutterEnabled =
                hasCameraPermission && !postViewModel.isCapturing && !postViewModel.isUploading,
            onPickFromRoll = {
                postViewModel.clearMessages()
                pickerFailed = false
                try {
                    cameraRollLauncher.launch(arrayOf("image/*"))
                } catch (error: ActivityNotFoundException) {
                    Log.e("PostScreen", "No file picker available", error)
                    pickerFailed = true
                }
            },
            onTakePhoto = {
                pickerFailed = false
                postViewModel.requestPhotoCapture()
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CameraPermissionRequest(
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(
                if (permissionDenied) {
                    R.string.camera_permission_settings
                } else {
                    R.string.camera_permission_required
                }
            )
        )
        Button(
            onClick = if (permissionDenied) onOpenSettings else onRequestPermission,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                stringResource(
                    if (permissionDenied) {
                        R.string.open_settings
                    } else {
                        R.string.grant_permission
                    }
                )
            )
        }
    }
}

@Composable
private fun CameraBottomControls(
    captureFailed: Boolean,
    pickerFailed: Boolean,
    showUploadedMessage: Boolean,
    shutterEnabled: Boolean,
    onPickFromRoll: () -> Unit,
    onTakePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showUploadedMessage) {
            Text(stringResource(R.string.photo_uploaded), color = textColor)
        }
        if (captureFailed) {
            Text(stringResource(R.string.photo_camera_failed), color = textColor)
        }
        if (pickerFailed) {
            Text(stringResource(R.string.photo_picker_failed), color = textColor)
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            CameraRollButton(
                onClick = onPickFromRoll,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            ShutterButton(
                enabled = shutterEnabled,
                onClick = onTakePhoto,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun CameraRollButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val contentColor = MaterialTheme.colorScheme.onPrimary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
        border = BorderStroke(2.dp, contentColor),
        modifier = modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_photo_library),
                contentDescription = stringResource(R.string.choose_from_camera_roll),
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shutterColor = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(4.dp, shutterColor, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(shutterColor)
            .clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun PostDetailsForm(postViewModel: PostViewModel, modifier: Modifier = Modifier) {
    var showPostDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Button(
                enabled = !postViewModel.isUploading,
                onClick = { postViewModel.retakePhoto() }
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
            postViewModel.isUploading -> R.string.photo_uploading
            postViewModel.uploadFailed -> R.string.photo_upload_failed
            else -> R.string.photo_ready
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

// The live camera view, which also takes the picture for [imageCapture]. It's bound once, when

// the view is created, and unbinds when the screen's lifecycle ends.
@Composable
private fun CameraPreview(imageCapture: ImageCapture, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var bindingFailed by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        onDispose {
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
                        } catch (error: IllegalStateException) {
                            Log.e("PostScreen", "CameraX use case binding failed", error)
                            bindingFailed = true
                        } catch (error: IllegalArgumentException) {
                            Log.e("PostScreen", "CameraX use case binding failed", error)
                            bindingFailed = true
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (bindingFailed) {
            Text(
                text = stringResource(R.string.photo_camera_failed),
                color = MaterialTheme.colorScheme.onPrimary
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
