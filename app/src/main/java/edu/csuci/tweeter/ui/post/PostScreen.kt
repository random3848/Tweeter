package edu.csuci.tweeter.ui.post

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.location.LocationFix
import edu.csuci.tweeter.location.LocationSource
import edu.csuci.tweeter.ui.auth.LogInPrompt
import edu.csuci.tweeter.ui.location.LocationPicker
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

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
 * Takes a photo with the live camera or picks one from the camera roll, finds where it was taken,
 * and lets the user move that location on a map and share the photo as a post. Guests can only
 * browse, so while nobody is logged in it asks the user to log in instead.
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

    if (loggedInUid == null) {
        LogInPrompt(
            title = stringResource(R.string.post_log_in_title),
            onLogIn = onLogIn,
            onCreateAccount = onCreateAccount,
            modifier = modifier
        )
    } else {
        PostForm(
            postViewModel = postViewModel,
            onClose = onClose,
            modifier = modifier
        )
    }
}

@Composable
private fun PostForm(
    postViewModel: PostViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraFailed by rememberSaveable { mutableStateOf(false) }
    var pickerFailed by rememberSaveable { mutableStateOf(false) }
    var isFromCamera by rememberSaveable { mutableStateOf(false) }
    var showPostDialog by rememberSaveable { mutableStateOf(false) }

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
        photoUri?.let { uri ->
            postViewModel.locatePhoto(Uri.parse(uri), capturedInApp = isFromCamera)
        }
    }

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
                        onClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
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

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 32.dp)
            ) {
                Surface(
                    onClick = {
                        cameraFailed = false
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
                            contentDescription = stringResource(R.string.choose_from_camera_roll),
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
                        .clickable(enabled = hasCameraPermission && !postViewModel.isUploading) {
                            cameraFailed = false
                            pickerFailed = false
                            try {
                                val directory = File(context.cacheDir, "photos")
                                if (!directory.isDirectory && !directory.mkdirs()) {
                                    throw IOException("Could not create photo directory")
                                }
                                val file = File.createTempFile("photo_", ".jpg", directory)
                                pendingPath = file.absolutePath

                                val outputOptions = ImageCapture.OutputFileOptions.Builder(
                                    file
                                ).build()
                                imageCapture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(
                                            outputFileResults: ImageCapture.OutputFileResults
                                        ) {
                                            val savedUri = outputFileResults.savedUri ?:
                                                FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    file
                                                )
                                            deleteCachedPhoto(photoPath)
                                            postViewModel.clearUploadResult()
                                            photoUri = savedUri.toString()
                                            photoPath = pendingPath
                                            pendingPath = null
                                            isFromCamera = true
                                            locateNewPhoto()
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e("PostScreen", "Camera capture failed", exception)
                                            deleteCachedPhoto(pendingPath)
                                            pendingPath = null
                                            cameraFailed = true
                                        }
                                    }
                                )
                            } catch (error: IOException) {
                                Log.e("PostScreen", "Could not create camera photo file", error)
                                deleteCachedPhoto(pendingPath)
                                pendingPath = null
                                cameraFailed = true
                            }
                        }
                )
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
                    onClick = {
                        deleteCachedPhoto(photoPath)
                        photoPath = null
                        photoUri = null
                    }
                ) {
                    Text(stringResource(R.string.camera_retake))
                }
            }

            Button(
                enabled = photoUri != null &&
                    !postViewModel.isUploading &&
                    pendingPath == null &&
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
                            postViewModel.uploadPhoto(
                                Uri.parse(uri),
                                caption,
                                animalType,
                                confidence
                            )
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
}

@Composable
private fun CameraPreview(imageCapture: ImageCapture, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = modifier,
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (exc: Exception) {
                    Log.e("PostScreen", "CameraX use case binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(context))
        }
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
