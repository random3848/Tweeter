package edu.csuci.tweeter.ui.profile

import android.content.ActivityNotFoundException
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.camera.CameraControls

/**
 * "Change photo", which opens a bottom sheet to take a picture, choose one from the camera roll,
 * or remove the current photo.
 *
 * Camera roll photos come from Android's photo picker, which leaves out where they were taken.
 *
 * @param enabled Whether the photo can be changed now; it can't while the profile is saved.
 * @param hasPhoto Whether there's a photo to remove; "Remove photo" is offered only then.
 * @param camera Takes the picture, and says whether the latest one couldn't be taken.
 * @param onPhotoChosen Called with the URI of the photo chosen from the camera roll.
 * @param onRemovePhoto Called when the user removes the photo.
 * @param modifier Applied to the button and the message shown when the camera or camera roll
 *     can't open.
 */
@OptIn(ExperimentalMaterial3Api::class) // For ModalBottomSheet, still experimental in Material 3.
@Composable
fun ChangePhotoButton(
    enabled: Boolean,
    hasPhoto: Boolean,
    camera: CameraControls,
    onPhotoChosen: (photoUri: String) -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSheetOpen by rememberSaveable { mutableStateOf(false) }
    var pickerFailed by rememberSaveable { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPhotoChosen(uri.toString())
        }
    }

    fun chooseFromCameraRoll() {
        try {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (error: ActivityNotFoundException) {
            Log.e("ChangePhotoButton", "No photo picker", error)
            pickerFailed = true
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(
            enabled = enabled,
            onClick = {
                pickerFailed = false
                camera.dismissCameraFailure()
                isSheetOpen = true
            }
        ) {
            Text(text = stringResource(R.string.edit_profile_change_photo))
        }
        failureMessage(camera.cameraFailed, pickerFailed)?.let { message ->
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
                camera.takePicture()
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

// The string resource of why the camera or camera roll didn't open, or null when neither failed.
@StringRes
private fun failureMessage(cameraFailed: Boolean, pickerFailed: Boolean): Int? = when {
    cameraFailed -> R.string.photo_camera_failed
    pickerFailed -> R.string.photo_picker_failed
    else -> null
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
