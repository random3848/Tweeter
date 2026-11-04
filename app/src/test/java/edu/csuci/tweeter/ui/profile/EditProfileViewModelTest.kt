package edu.csuci.tweeter.ui.profile

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.FirebaseException
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PhotoUploadException
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {
    private val profile = UserProfile(
        uid = "uid",
        displayName = "Maya Kim",
        bio = "Spiders, mostly.",
        photoUrl = ""
    )
    private val profileRepository = FakeProfileRepository(profile)
    private val photoRepository = FakePhotoRepository()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loggedIn_editsStartFromProfile() {
        val viewModel = createViewModel()

        assertEquals(ProfileEdits.of(profile), viewModel.edits)
        assertFalse(viewModel.isDone)
    }

    @Test
    fun init_nobodyLoggedIn_isDone() {
        val viewModel = createViewModel(loggedInUid = null)

        assertNull(viewModel.edits)
        assertTrue(viewModel.isDone)
    }

    @Test
    fun init_profileCantBeRead_isDone() {
        profileRepository.failsToRead = true

        val viewModel = createViewModel()

        assertTrue(viewModel.isDone)
    }

    @Test
    fun init_savedEdits_restoresThem() {
        val savedStateHandle = SavedStateHandle()
        createViewModel(savedStateHandle = savedStateHandle).updateDisplayName("Maya Lin")

        val restored = createViewModel(savedStateHandle = savedStateHandle)

        assertEquals("Maya Lin", restored.edits?.displayName)
    }

    @Test
    fun updateDisplayName_atLimit_changesEdits() {
        val viewModel = createViewModel()
        val name = "a".repeat(UserProfile.MAX_DISPLAY_NAME_LENGTH)

        viewModel.updateDisplayName(name)

        assertEquals(name, viewModel.edits?.displayName)
    }

    @Test
    fun updateDisplayName_overLimit_ignored() {
        val viewModel = createViewModel()

        viewModel.updateDisplayName("a".repeat(UserProfile.MAX_DISPLAY_NAME_LENGTH + 1))

        assertEquals(profile.displayName, viewModel.edits?.displayName)
    }

    @Test
    fun updateBio_atLimit_changesEdits() {
        val viewModel = createViewModel()
        val bio = "a".repeat(UserProfile.MAX_BIO_LENGTH)

        viewModel.updateBio(bio)

        assertEquals(bio, viewModel.edits?.bio)
    }

    @Test
    fun updateBio_overLimit_ignored() {
        val viewModel = createViewModel()

        viewModel.updateBio("a".repeat(UserProfile.MAX_BIO_LENGTH + 1))

        assertEquals(profile.bio, viewModel.edits?.bio)
    }

    @Test
    fun canSave_blankName_false() {
        val viewModel = createViewModel()

        viewModel.updateDisplayName("  ")

        assertFalse(viewModel.canSave)
    }

    @Test
    fun save_validEdits_savesTrimmedProfileAndIsDone() {
        val viewModel = createViewModel()
        viewModel.updateDisplayName(" Maya Lin ")
        viewModel.updateBio("Birds, now. ")

        viewModel.save()

        assertEquals(
            profile.copy(displayName = "Maya Lin", bio = "Birds, now."),
            profileRepository.savedProfile
        )
        assertTrue(viewModel.isDone)
    }

    @Test
    fun save_blankName_savesNothing() {
        val viewModel = createViewModel()
        viewModel.updateDisplayName("")

        viewModel.save()

        assertNull(profileRepository.savedProfile)
        assertFalse(viewModel.isDone)
    }

    @Test
    fun save_repositoryFails_reportsFailureAndStaysOpen() {
        profileRepository.failsToSave = true
        val viewModel = createViewModel()

        viewModel.save()

        assertTrue(viewModel.saveFailed)
        assertFalse(viewModel.isSaving)
        assertFalse(viewModel.isDone)
    }

    @Test
    fun hasPhoto_profileWithPhoto_true() {
        profileRepository.profile = profile.copy(photoUrl = PHOTO_URL)

        assertTrue(createViewModel().hasPhoto)
    }

    @Test
    fun hasPhoto_profileWithoutPhoto_false() {
        assertFalse(createViewModel().hasPhoto)
    }

    @Test
    fun choosePhoto_avatarShowsChosenPhoto() {
        val viewModel = createViewModel()

        viewModel.choosePhoto(CHOSEN_PHOTO_URI)

        assertEquals(CHOSEN_PHOTO_URI, viewModel.avatarProfile?.photoUrl)
        assertTrue(viewModel.hasPhoto)
    }

    @Test
    fun removePhoto_avatarShowsNoPhoto() {
        profileRepository.profile = profile.copy(photoUrl = PHOTO_URL)
        val viewModel = createViewModel()

        viewModel.removePhoto()

        assertEquals("", viewModel.avatarProfile?.photoUrl)
        assertFalse(viewModel.hasPhoto)
    }

    @Test
    fun init_savedPhotoChange_restoresIt() {
        val savedStateHandle = SavedStateHandle()
        createViewModel(savedStateHandle = savedStateHandle).choosePhoto(CHOSEN_PHOTO_URI)

        val restored = createViewModel(savedStateHandle = savedStateHandle)

        assertEquals(CHOSEN_PHOTO_URI, restored.avatarProfile?.photoUrl)
    }

    @Test
    fun choosePhoto_afterCameraPhoto_deletesCameraFile() {
        val viewModel = createViewModel()
        val cameraFile = temporaryFolder.newFile("camera.jpg")
        viewModel.choosePhoto(CHOSEN_PHOTO_URI, cachedFile = cameraFile)

        viewModel.choosePhoto("content://photos/2")

        assertFalse(cameraFile.exists())
    }

    @Test
    fun removePhoto_afterCameraPhoto_deletesCameraFile() {
        val viewModel = createViewModel()
        val cameraFile = temporaryFolder.newFile("camera.jpg")
        viewModel.choosePhoto(CHOSEN_PHOTO_URI, cachedFile = cameraFile)

        viewModel.removePhoto()

        assertFalse(cameraFile.exists())
    }

    @Test
    fun save_photoChosen_uploadsItAndSavesItsUrl() {
        val viewModel = createViewModel()
        viewModel.choosePhoto(CHOSEN_PHOTO_URI)

        viewModel.save()

        assertEquals(CHOSEN_PHOTO_URI, photoRepository.uploadedPhotoUri)
        assertEquals(FakePhotoRepository.UPLOADED_URL, profileRepository.savedProfile?.photoUrl)
        assertTrue(viewModel.isDone)
    }

    @Test
    fun save_photoRemoved_savesNoPhotoWithoutUploading() {
        profileRepository.profile = profile.copy(photoUrl = PHOTO_URL)
        val viewModel = createViewModel()
        viewModel.removePhoto()

        viewModel.save()

        assertNull(photoRepository.uploadedPhotoUri)
        assertEquals("", profileRepository.savedProfile?.photoUrl)
    }

    @Test
    fun save_photoUnchanged_keepsPhotoWithoutUploading() {
        profileRepository.profile = profile.copy(photoUrl = PHOTO_URL)
        val viewModel = createViewModel()

        viewModel.save()

        assertNull(photoRepository.uploadedPhotoUri)
        assertEquals(PHOTO_URL, profileRepository.savedProfile?.photoUrl)
    }

    @Test
    fun save_uploadFails_reportsFailureAndSavesNothing() {
        photoRepository.failsToUpload = true
        val viewModel = createViewModel()
        viewModel.choosePhoto(CHOSEN_PHOTO_URI)

        viewModel.save()

        assertTrue(viewModel.saveFailed)
        assertNull(profileRepository.savedProfile)
        assertFalse(viewModel.isDone)
    }

    private fun createViewModel(
        loggedInUid: String? = profile.uid,
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ): EditProfileViewModel = EditProfileViewModel(
        profileRepository = profileRepository,
        photoRepository = photoRepository,
        accountRepository = FakeAccountRepository(loggedInUid),
        savedStateHandle = savedStateHandle
    )

    private class FakeProfileRepository(var profile: UserProfile) : ProfileRepository {
        var failsToRead = false
        var failsToSave = false
        var savedProfile: UserProfile? = null

        override fun getProfile(uid: String): Flow<UserProfile> = if (failsToRead) {
            flow { throw FirebaseException("Could not read the profile") }
        } else {
            flowOf(profile)
        }

        override suspend fun saveProfile(profile: UserProfile) {
            if (failsToSave) {
                throw FirebaseException("Could not save the profile")
            }
            savedProfile = profile
        }
    }

    private class FakePhotoRepository : PhotoRepository {
        var failsToUpload = false
        var uploadedPhotoUri: String? = null

        override suspend fun uploadPhoto(uri: Uri): String = error("Posts aren't edited here")

        override suspend fun uploadProfilePhoto(photoUri: String): String {
            if (failsToUpload) {
                throw PhotoUploadException(IOException("Could not upload the photo"))
            }
            uploadedPhotoUri = photoUri
            return UPLOADED_URL
        }

        companion object {
            const val UPLOADED_URL = "https://example.com/uploaded.jpg"
        }
    }

    private class FakeAccountRepository(uid: String?) : AccountRepository {
        override val loggedInUid: StateFlow<String?> = MutableStateFlow(uid)

        override suspend fun logIn(email: String, password: String) = Unit

        override suspend fun createAccount(email: String, password: String) = Unit

        override suspend fun logOut() = Unit

        override suspend fun resetPassword(email: String) = Unit
    }

    private companion object {
        const val PHOTO_URL = "https://example.com/maya.jpg"
        const val CHOSEN_PHOTO_URI = "content://photos/1"
    }
}
