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
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import edu.csuci.tweeter.data.getLoggedInProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val DISPLAY_NAME_KEY = "displayName"
private const val BIO_KEY = "bio"

/**
 * Reads the logged-in user's profile, holds the name and bio they type on [EditProfileScreen],
 * and saves them.
 *
 * What they type survives the app being stopped in the background, through [savedStateHandle].
 *
 * @param profileRepository Where the profile is read from and saved to.
 * @param accountRepository Who is logged in.
 * @param savedStateHandle Keeps the typed name and bio.
 */
class EditProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val accountRepository: AccountRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    /** The profile as it's stored, before the edits, or null while it's still being read. */
    var profile by mutableStateOf<UserProfile?>(null)
        private set

    /** The name and bio as typed, or null while the profile is still being read. */
    var edits by mutableStateOf<ProfileEdits?>(null)
        private set

    /** Whether the edits are being saved. */
    var isSaving by mutableStateOf(false)
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

    /** Saves the edits to the profile, then sets [isDone]. Ignored unless [canSave]. */
    fun save() {
        val currentProfile = profile ?: return
        val currentEdits = edits ?: return
        if (!canSave) {
            return
        }

        isSaving = true
        saveFailed = false
        viewModelScope.launch { saveProfile(currentEdits.applyTo(currentProfile)) }
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

    private suspend fun saveProfile(editedProfile: UserProfile) {
        try {
            profileRepository.saveProfile(editedProfile)
            isDone = true
        } catch (error: FirebaseException) {
            Log.e("EditProfileViewModel", "Could not save the profile", error)
            saveFailed = true
        } finally {
            isSaving = false
        }
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
                EditProfileViewModel(
                    profileRepository = application.container.profileRepository,
                    accountRepository = application.container.accountRepository,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
