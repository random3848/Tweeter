package edu.csuci.tweeter.ui.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import edu.csuci.tweeter.R
import java.io.File
import java.io.IOException

// Where camera photos are written, inside the app's cache directory. FileProvider shares this
// folder with the camera app (the "photos" path in res/xml/file_paths.xml).
private const val CAMERA_DIRECTORY = "photos"

/**
 * "Change photo", which opens a bottom sheet to take a picture, choose one from the camera roll,
 * or remove the current photo.
 *
 * Camera roll photos come from Android's photo picker, which leaves out where they were taken.
 *
 * @param hasPhoto Whether there's a photo to remove; "Remove photo" is offered only then.
 * @param onPhotoChosen Called with the new photo's URI and, for a camera photo, the app's own file
 *     holding it.
 * @param onRemovePhoto Called when the user removes the photo.
 * @param modifier Applied to the button and the message shown when the camera or camera roll
 *     can't open.
 */
@OptIn(ExperimentalMaterial3Api::class) // For ModalBottomSheet, still experimental in Material 3.
@Composable
fun ChangePhotoButton(
    hasPhoto: Boolean,
    onPhotoChosen: (photoUri: String, cachedFile: File?) -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSheetOpen by rememberSaveable { mutableStateOf(false) }
    // Survives the app being stopped while the camera app is open.
    var cameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    // The string resource of why the camera or camera roll didn't open, or null.
    var failureMessageId by rememberSaveable { mutableStateOf<Int?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { isSaved ->
        val file = cameraFilePath?.let(::File) ?: return@rememberLauncherForActivityResult
        cameraFilePath = null
        if (isSaved) {
            onPhotoChosen(Uri.fromFile(file).toString(), file)
        } else {
            file.delete()
        }
    }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPhotoChosen(uri.toString(), null)
        }
    }

    fun takePicture() {
        val file = try {
            createCameraFile(context)
        } catch (error: IOException) {
            Log.e("ChangePhotoButton", "Could not create a file for the camera photo", error)
            failureMessageId = R.string.photo_camera_failed
            return
        }

        try {
            cameraFilePath = file.path
            cameraLauncher.launch(
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            )
        } catch (error: ActivityNotFoundException) {
            Log.e("ChangePhotoButton", "No camera app", error)
            file.delete()
            cameraFilePath = null
            failureMessageId = R.string.photo_camera_failed
        }
    }

    fun chooseFromCameraRoll() {
        try {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (error: ActivityNotFoundException) {
            Log.e("ChangePhotoButton", "No photo picker", error)
            failureMessageId = R.string.photo_picker_failed
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(
            onClick = {
                failureMessageId = null
                isSheetOpen = true
            }
        ) {
            Text(text = stringResource(R.string.edit_profile_change_photo))
        }
        failureMessageId?.let { message ->
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (isSheetOpen) {
        ModalBottomSheet(onDismissRequest = { isSheetOpen = false }) {
            SheetOption(R.string.take_picture) {
                isSheetOpen = false
                takePicture()
            }
            SheetOption(R.string.choose_from_camera_roll) {
                isSheetOpen = false
                chooseFromCameraRoll()
            }
            if (hasPhoto) {
                SheetOption(
                    R.string.edit_profile_remove_photo,
                    color = MaterialTheme.colorScheme.error
                ) {
                    isSheetOpen = false
                    onRemovePhoto()
                }
            }
            SheetOption(R.string.edit_profile_cancel) {
                isSheetOpen = false
            }
        }
    }
}

@Composable
private fun SheetOption(
    @StringRes label: Int,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

private fun createCameraFile(context: Context): File {
    val directory = File(context.cacheDir, CAMERA_DIRECTORY)
    if (!directory.isDirectory && !directory.mkdirs()) {
        throw IOException("Could not create $directory")
    }
    return File.createTempFile("profile_", ".jpg", directory)
}
