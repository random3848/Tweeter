package edu.csuci.tweeter.ui.profile

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.FirebaseException
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PhotoUploadException
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.ProfileSaveException
import edu.csuci.tweeter.data.ProfileSaver
import edu.csuci.tweeter.data.UserProfile
import edu.csuci.tweeter.data.getLoggedInProfile
import edu.csuci.tweeter.ui.camera.CameraCapture
import edu.csuci.tweeter.ui.camera.CameraControls
import edu.csuci.tweeter.ui.camera.ChosenPhoto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val DISPLAY_NAME_KEY = "displayName"
private const val BIO_KEY = "bio"
private const val PHOTO_REMOVED_KEY = "photoRemoved"

/**
 * How long saving waits for the server to confirm the profile write. Firestore applies the write
 * on the phone at once and sends it when it can, so a write still unconfirmed after this, usually
 * because the phone is offline, counts as saved. [ProfileSaver] still waits for it.
 */
internal const val PROFILE_WRITE_TIMEOUT_MILLIS = 10_000L

/**
 * Reads the logged-in user's profile, holds the name, bio and photo they choose on
 * [EditProfileScreen], and saves them.
 *
 * What they choose survives the app being stopped in the background, through [savedStateHandle].
 *
 * @param profileRepository Where the profile is read from.
 * @param photoRepository Uploads a new profile photo when the profile is saved.
 * @param profileSaver Saves the profile, deleting the photo the save leaves unused, even once
 *     this ViewModel has stopped waiting for it.
 * @param accountRepository Who is logged in.
 * @param photo The new profile photo, chosen with the camera or from the camera roll. It isn't
 *     replaced while the profile saves.
 * @param cameraCapture Takes pictures with the camera app into [photo], for the screen to control
 *     through this ViewModel; it's closed with the ViewModel.
 * @param savedStateHandle Keeps the typed name and bio, and whether the photo was removed.
 */
class EditProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val photoRepository: PhotoRepository,
    private val profileSaver: ProfileSaver,
    private val accountRepository: AccountRepository,
    private val photo: ChosenPhoto,
    private val cameraCapture: CameraCapture,
    private val savedStateHandle: SavedStateHandle
) : ViewModel(cameraCapture),
    CameraControls by cameraCapture {
    private var stage by mutableStateOf<Stage>(Stage.Loading)

    // Whether the user removed the photo; a photo chosen since replaces it all the same.
    private var isPhotoRemoved by mutableStateOf(
        savedStateHandle.get<Boolean>(PHOTO_REMOVED_KEY) ?: false
    )

    // The profile being edited, or null while it's read and when there's none to edit.
    private val editing: Stage.Editing?
        get() = stage as? Stage.Editing

    /** What saving does with the profile photo. */
    val photoChange: PhotoChange
        get() {
            val chosenUri = photo.uri
            return when {
                chosenUri != null -> PhotoChange.Replace(chosenUri)
                isPhotoRemoved -> PhotoChange.Remove
                else -> PhotoChange.Unchanged
            }
        }

    /**
     * The name and bio as typed, or null while the profile is still being read and when there's
     * no profile to edit.
     */
    val edits: ProfileEdits?
        get() = editing?.edits

    /** Whether the edits are being saved. */
    val isSaving: Boolean
        get() = editing?.save?.isRunning == true

    /**
     * Whether saving is writing the profile, after any new photo has uploaded. The write can't be
     * taken back, so the screen doesn't let the user leave until it's done. Leaving while the
     * photo uploads is fine: it stops the upload and nothing is saved.
     */
    val isWritingProfile: Boolean
        get() = editing?.save == SaveStatus.WRITING_PROFILE

    /** Whether the latest attempt to save failed. */
    val saveFailed: Boolean
        get() = editing?.save == SaveStatus.FAILED

    /**
     * Whether the screen is finished and should close: the edits were saved, or there's no
     * profile to edit because nobody is logged in or it couldn't be read.
     */
    val isDone: Boolean
        get() = when (val current = stage) {
            Stage.Loading -> false
            Stage.NothingToEdit -> true
            is Stage.Editing -> current.save == SaveStatus.SAVED
        }

    /**
     * Whether [save] would save: the edits are valid, no save is running and the screen isn't
     * [isDone].
     */
    val canSave: Boolean
        get() {
            val current = editing ?: return false
            return current.edits.isValid && current.save.canStart
        }

    /**
     * The profile as its avatar shows it while editing, or null while it's still being read: with
     * the chosen photo, whose `photoUrl` is then a URI on the phone, or none once it's removed.
     */
    val avatarProfile: UserProfile?
        get() {
            val currentProfile = editing?.profile ?: return null
            val photoUrl = when (val change = photoChange) {
                PhotoChange.Unchanged -> currentProfile.photoUrl
                is PhotoChange.Replace -> change.photoUri
                PhotoChange.Remove -> ""
            }
            return currentProfile.copy(photoUrl = photoUrl)
        }

    /** Whether the avatar shows a photo, which the user can then remove. */
    val hasPhoto: Boolean
        get() = avatarProfile?.photoUrl?.isNotEmpty() == true

    init {
        viewModelScope.launch { loadProfile() }
    }

    /** Sets the typed name to [displayName], unless it's longer than a name can be. */
    fun updateDisplayName(displayName: String) {
        val currentEdits = edits ?: return
        if (displayName.length <= UserProfile.MAX_DISPLAY_NAME_LENGTH) {
            updateEdits(currentEdits.copy(displayName = displayName))
        }
    }

    /** Sets the typed bio to [bio], unless it's longer than a bio can be. */
    fun updateBio(bio: String) {
        val currentEdits = edits ?: return
        if (bio.length <= UserProfile.MAX_BIO_LENGTH) {
            updateEdits(currentEdits.copy(bio = bio))
        }
    }

    /**
     * Makes the photo at [photoUri], chosen from the camera roll, the new profile photo, uploaded
     * when the profile is saved. Ignored while [isSaving], since saving reads the photo it
     * started with.
     */
    fun choosePhoto(photoUri: String) {
        photo.chooseFromCameraRoll(photoUri)
    }

    /** Removes the profile photo when the profile is saved. Ignored while [isSaving]. */
    fun removePhoto() {
        if (isSaving) {
            return
        }

        photo.clear()
        isPhotoRemoved = true
        savedStateHandle[PHOTO_REMOVED_KEY] = true
    }

    /**
     * Saves the edits and the photo change to the profile, uploading a new photo first, then
     * sets [isDone]. Ignored unless [canSave].
     */
    fun save() {
        val current = editing ?: return
        if (!canSave) {
            return
        }

        updateSave(SaveStatus.UPLOADING_PHOTO)
        photo.lock()
        val change = photoChange
        viewModelScope.launch { saveProfile(current.edits.applyTo(current.profile), change) }
    }

    private suspend fun loadProfile() {
        val loggedInProfile = readLoggedInProfile()
        stage = if (loggedInProfile == null) {
            Stage.NothingToEdit
        } else {
            Stage.Editing(
                profile = loggedInProfile,
                edits = savedEdits() ?: ProfileEdits.of(loggedInProfile),
                save = SaveStatus.NOT_STARTED
            )
        }
    }

    // Null when nobody is logged in or the profile can't be read; either way there's nothing to
    // edit.
    private suspend fun readLoggedInProfile(): UserProfile? = try {
        profileRepository.getLoggedInProfile(accountRepository).first()
    } catch (error: FirebaseException) {
        Log.e("EditProfileViewModel", "Could not read the profile", error)
        null
    }

    private suspend fun saveProfile(editedProfile: UserProfile, change: PhotoChange) {
        try {
            saveWithPhoto(editedProfile, change)
            updateSave(SaveStatus.SAVED)
        } catch (error: PhotoUploadException) {
            Log.e("EditProfileViewModel", "Could not upload the profile photo", error)
            updateSave(SaveStatus.FAILED)
        } catch (error: ProfileSaveException) {
            Log.e("EditProfileViewModel", "Could not save the profile", error)
            updateSave(SaveStatus.FAILED)
        } finally {
            photo.unlock()
        }
    }

    // Writes [editedProfile] with the photo [change] leaves it, uploading a new photo first.
    private suspend fun saveWithPhoto(editedProfile: UserProfile, change: PhotoChange) {
        val photoUrl = photoUrlToSave(editedProfile.photoUrl, change)
        writeProfile(editedProfile.copy(photoUrl = photoUrl), editedProfile.photoUrl)
    }

    // Writes [profileToWrite], whose photo replaces [previousPhotoUrl].
    private suspend fun writeProfile(profileToWrite: UserProfile, previousPhotoUrl: String) {
        updateSave(SaveStatus.WRITING_PROFILE)
        val saving = profileSaver.save(profileToWrite, previousPhotoUrl)
        val confirmed = withTimeoutOrNull(PROFILE_WRITE_TIMEOUT_MILLIS) { saving.await() }
        if (confirmed == null) {
            Log.w("EditProfileViewModel", "Profile saved on the phone; it syncs when back online")
        }
    }

    // Uploads a replaced photo, so this can throw PhotoUploadException.
    private suspend fun photoUrlToSave(currentPhotoUrl: String, change: PhotoChange): String =
        when (change) {
            PhotoChange.Unchanged -> currentPhotoUrl
            is PhotoChange.Replace -> photoRepository.uploadProfilePhoto(change.photoUri)
            PhotoChange.Remove -> ""
        }

    private fun savedEdits(): ProfileEdits? {
        val displayName = savedStateHandle.get<String>(DISPLAY_NAME_KEY) ?: return null
        val bio = savedStateHandle.get<String>(BIO_KEY) ?: return null
        return ProfileEdits(displayName = displayName, bio = bio)
    }

    private fun updateEdits(newEdits: ProfileEdits) {
        stage = editingNow().copy(edits = newEdits)
        savedStateHandle[DISPLAY_NAME_KEY] = newEdits.displayName
        savedStateHandle[BIO_KEY] = newEdits.bio
    }

    private fun updateSave(newSave: SaveStatus) {
        stage = editingNow().copy(save = newSave)
    }

    // Only a profile being edited is changed or saved, and it stays loaded until the screen
    // closes, also while a save runs.
    private fun editingNow(): Stage.Editing = checkNotNull(editing) { "No profile is being edited" }

    // Where the screen is: reading the profile, editing it, or with no profile to edit.
    private sealed interface Stage {
        // The profile is still being read.
        data object Loading : Stage

        // Nobody is logged in or the profile couldn't be read, so the screen closes.
        data object NothingToEdit : Stage

        // The user edits [profile], as it's stored, into [edits], which saving is at [save].
        data class Editing(
            val profile: UserProfile,
            val edits: ProfileEdits,
            val save: SaveStatus
        ) : Stage
    }

    // How far saving the edits has got, and whether a save can start from there. The edits can
    // still change while a save runs; it saves the ones it started with.
    private enum class SaveStatus(val isRunning: Boolean, val canStart: Boolean) {
        // Not saved yet.
        NOT_STARTED(isRunning = false, canStart = true),

        // Uploading the new photo first, if there is one. Leaving now saves nothing.
        UPLOADING_PHOTO(isRunning = true, canStart = false),

        // Writing the profile, which can't be taken back.
        WRITING_PROFILE(isRunning = true, canStart = false),

        // The latest save failed; the user can save again.
        FAILED(isRunning = false, canStart = true),

        // Saved, so the screen closes, and nothing saves again.
        SAVED(isRunning = false, canStart = false)
    }

    companion object {
        /** Creates an [EditProfileViewModel] that uses the app's shared repositories. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                val cameraFileStore = application.container.cameraFileStore
                val savedStateHandle = createSavedStateHandle()
                val photo = ChosenPhoto(cameraFileStore, savedStateHandle)
                EditProfileViewModel(
                    profileRepository = application.container.profileRepository,
                    photoRepository = application.container.photoRepository,
                    profileSaver = application.container.profileSaver,
                    accountRepository = application.container.accountRepository,
                    photo = photo,
                    cameraCapture = CameraCapture.onMainThread(
                        cameraFileStore,
                        photo,
                        savedStateHandle
                    ),
                    savedStateHandle = savedStateHandle
                )
            }
        }
    }
}
