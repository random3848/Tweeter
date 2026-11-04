package edu.csuci.tweeter.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.UserProfile

/**
 * Lets the logged-in user change their photo, name and bio. Save stores them and returns to
 * Profile; Cancel, or going back, returns without saving.
 *
 * It has its own top bar, and the app hides its bottom bar here.
 *
 * @param onClose Returns to Profile.
 * @param modifier Applied to the whole screen.
 * @param editProfileViewModel Reads the profile and saves the edits.
 */
@Composable
fun EditProfileScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    editProfileViewModel: EditProfileViewModel = viewModel(factory = EditProfileViewModel.factory)
) {
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(editProfileViewModel.isDone) {
        if (editProfileViewModel.isDone) {
            currentOnClose()
        }
    }

    val avatarProfile = editProfileViewModel.avatarProfile
    val edits = editProfileViewModel.edits
    Column(modifier = modifier.fillMaxSize()) {
        EditProfileTopBar(
            canSave = editProfileViewModel.canSave,
            onCancel = onClose,
            onSave = editProfileViewModel::save
        )

        // Until the profile has been read there's nothing to edit; show only the top bar.
        if (avatarProfile != null && edits != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileAvatar(
                    profile = avatarProfile,
                    size = 96.dp,
                    initialsStyle = MaterialTheme.typography.headlineMedium
                )
                ChangePhotoButton(
                    hasPhoto = editProfileViewModel.hasPhoto,
                    onPhotoChosen = editProfileViewModel::choosePhoto,
                    onRemovePhoto = editProfileViewModel::removePhoto
                )
                DisplayNameField(
                    displayName = edits.displayName,
                    onDisplayNameChange = editProfileViewModel::updateDisplayName
                )
                BioField(bio = edits.bio, onBioChange = editProfileViewModel::updateBio)
                if (editProfileViewModel.saveFailed) {
                    Text(
                        text = stringResource(R.string.edit_profile_save_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// Cancel on the left, the title centered on the whole bar, and a bold Save on the right.
@Composable
private fun EditProfileTopBar(canSave: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp)
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(text = stringResource(R.string.edit_profile_cancel))
                }
                Text(
                    text = stringResource(R.string.profile_edit),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center)
                )
                TextButton(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text(
                        text = stringResource(R.string.edit_profile_save),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun DisplayNameField(displayName: String, onDisplayNameChange: (String) -> Unit) {
    val isBlank = displayName.isBlank()
    OutlinedTextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        label = { Text(text = stringResource(R.string.edit_profile_name)) },
        isError = isBlank,
        supportingText = if (isBlank) {
            { Text(text = stringResource(R.string.edit_profile_name_required)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BioField(bio: String, onBioChange: (String) -> Unit) {
    OutlinedTextField(
        value = bio,
        onValueChange = onBioChange,
        label = { Text(text = stringResource(R.string.edit_profile_bio)) },
        supportingText = {
            Text(
                text = stringResource(
                    R.string.edit_profile_bio_length,
                    bio.length,
                    UserProfile.MAX_BIO_LENGTH
                ),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        },
        minLines = 3,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth()
    )
}
