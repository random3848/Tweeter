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
    // The profile as it's stored, before the edits, or null while it's still being read.
    private var profile by mutableStateOf<UserProfile?>(null)

    /** The name and bio as typed, or null while the profile is still being read. */
    var edits by mutableStateOf<ProfileEdits?>(null)
        private set

    // Whether the user removed the photo; a photo chosen since replaces it all the same.
    private var isPhotoRemoved by mutableStateOf(
        savedStateHandle.get<Boolean>(PHOTO_REMOVED_KEY) ?: false
    )

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

    /** Whether the edits are being saved. */
    var isSaving by mutableStateOf(false)
        private set

    /**
     * Whether saving is writing the profile, after any new photo has uploaded. The write can't be
     * taken back, so the screen doesn't let the user leave until it's done. Leaving while the
     * photo uploads is fine: it stops the upload and nothing is saved.
     */
    var isWritingProfile by mutableStateOf(false)
        private set

    /** Whether the latest attempt to save failed. */
    var saveFailed by mutableStateOf(false)
        private set

    /**
     * Whether the screen is finished and should close: the edits were saved, or there's no
     * profile to edit because nobody is logged in or it couldn't be read.
     */
    var isDone by mutableStateOf(false)
        private set

    /** Whether [save] would save: the edits are valid and no save is running. */
    val canSave: Boolean
        get() = edits?.isValid == true && !isSaving

    /**
     * The profile as its avatar shows it while editing, or null while it's still being read: with
     * the chosen photo, whose `photoUrl` is then a URI on the phone, or none once it's removed.
     */
    val avatarProfile: UserProfile?
        get() {
            val currentProfile = profile ?: return null
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
        val currentProfile = profile ?: return
        val currentEdits = edits ?: return
        if (!canSave) {
            return
        }

        isSaving = true
        photo.lock()
        saveFailed = false
        val change = photoChange
        viewModelScope.launch { saveProfile(currentEdits.applyTo(currentProfile), change) }
    }

    private suspend fun loadProfile() {
        val loggedInProfile = readLoggedInProfile()
        if (loggedInProfile == null) {
            isDone = true
            return
        }

        profile = loggedInProfile
        edits = savedEdits() ?: ProfileEdits.of(loggedInProfile)
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
            isDone = true
        } catch (error: PhotoUploadException) {
            Log.e("EditProfileViewModel", "Could not upload the profile photo", error)
            saveFailed = true
        } catch (error: ProfileSaveException) {
            Log.e("EditProfileViewModel", "Could not save the profile", error)
            saveFailed = true
        } finally {
            isWritingProfile = false
            isSaving = false
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
        isWritingProfile = true
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
        edits = newEdits
        savedStateHandle[DISPLAY_NAME_KEY] = newEdits.displayName
        savedStateHandle[BIO_KEY] = newEdits.bio
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
