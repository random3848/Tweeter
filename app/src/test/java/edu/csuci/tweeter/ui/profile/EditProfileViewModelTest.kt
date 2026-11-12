package edu.csuci.tweeter.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.FirebaseException
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.CameraFileStore
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PhotoUploadException
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import edu.csuci.tweeter.ui.camera.CameraCapture
import edu.csuci.tweeter.ui.camera.ChosenPhoto
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
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

    private val dispatcher = UnconfinedTestDispatcher()

    // Outlives the ViewModels, as the app's scope does, to delete camera photos they discard.
    private val appScope = TestScope(dispatcher)

    private val cameraDirectory: File
        get() = File(temporaryFolder.root, "photos")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
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
    fun pictureSaved_avatarShowsIt() {
        val viewModel = createViewModel()

        val cameraUri = takeCameraPhoto(viewModel)

        assertEquals(cameraUri, viewModel.avatarProfile?.photoUrl)
        assertFalse(viewModel.isOpeningCamera)
    }

    @Test
    fun pictureCancelled_deletesFileAndKeepsPhoto() {
        val viewModel = createViewModel()
        openCamera(viewModel)

        viewModel.pictureCancelled()

        assertEquals(PhotoChange.Unchanged, viewModel.photoChange)
        assertEquals(emptyList<File>(), filesInDirectory())
    }

    @Test
    fun cameraUnavailable_failsAndDeletesFile() {
        val viewModel = createViewModel()
        viewModel.takePicture()

        viewModel.cameraUnavailable()

        assertTrue(viewModel.cameraFailed)
        assertEquals(emptyList<File>(), filesInDirectory())
    }

    @Test
    fun dismissCameraFailure_clearsFailure() {
        val viewModel = createViewModel()
        viewModel.takePicture()
        viewModel.cameraUnavailable()

        viewModel.dismissCameraFailure()

        assertFalse(viewModel.cameraFailed)
    }

    @Test
    fun choosePhoto_afterCameraPhoto_deletesCameraFile() {
        val viewModel = createViewModel()
        takeCameraPhoto(viewModel)

        viewModel.choosePhoto(CHOSEN_PHOTO_URI)

        assertEquals(emptyList<File>(), filesInDirectory())
    }

    @Test
    fun removePhoto_afterCameraPhoto_deletesCameraFile() {
        val viewModel = createViewModel()
        takeCameraPhoto(viewModel)

        viewModel.removePhoto()

        assertEquals(emptyList<File>(), filesInDirectory())
    }

    @Test
    fun choosePhoto_afterRemovingPhoto_replacesIt() {
        val viewModel = createViewModel()
        viewModel.removePhoto()

        viewModel.choosePhoto(CHOSEN_PHOTO_URI)

        assertEquals(PhotoChange.Replace(CHOSEN_PHOTO_URI), viewModel.photoChange)
    }

    @Test
    fun init_photoRemoved_restoresRemoval() {
        val savedStateHandle = SavedStateHandle()
        createViewModel(savedStateHandle = savedStateHandle).removePhoto()

        val restored = createViewModel(savedStateHandle = savedStateHandle)

        assertEquals(PhotoChange.Remove, restored.photoChange)
    }

    @Test
    fun cleared_afterCameraPhoto_restoredViewModelKeepsIt() {
        val savedStateHandle = SavedStateHandle()
        val store = ViewModelStore()
        val cameraUri = takeCameraPhoto(createViewModelIn(store, savedStateHandle))

        store.clear()
        val restored = createViewModel(savedStateHandle = savedStateHandle)

        assertEquals(PhotoChange.Replace(cameraUri), restored.photoChange)
        assertEquals(listOf(cameraUri), contentUrisInDirectory())
    }

    @Test
    fun cleared_whileCameraOpen_restoredViewModelKeepsPicture() {
        val savedStateHandle = SavedStateHandle()
        val store = ViewModelStore()
        val cameraUri = openCamera(createViewModelIn(store, savedStateHandle))

        store.clear()
        val restored = createViewModel(savedStateHandle = savedStateHandle)
        restored.pictureSaved()

        assertEquals(PhotoChange.Replace(cameraUri), restored.photoChange)
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

    @Test
    fun save_writeFailsAfterPhotoUploaded_deletesUploadedPhoto() {
        profileRepository.failsToSave = true
        val viewModel = createViewModel()
        viewModel.choosePhoto(CHOSEN_PHOTO_URI)

        viewModel.save()

        assertEquals(listOf(FakePhotoRepository.UPLOADED_URL), photoRepository.deletedPhotoUrls)
        assertTrue(viewModel.saveFailed)
    }

    @Test
    fun save_writeFailsWithPhotoUnchanged_deletesNothing() {
        profileRepository.profile = profile.copy(photoUrl = PHOTO_URL)
        profileRepository.failsToSave = true
        val viewModel = createViewModel()

        viewModel.save()

        assertEquals(emptyList<String>(), photoRepository.deletedPhotoUrls)
    }

    @Test
    fun save_photoUploadedAndSaved_deletesNothing() {
        val viewModel = createViewModel()
        viewModel.choosePhoto(CHOSEN_PHOTO_URI)

        viewModel.save()

        assertEquals(emptyList<String>(), photoRepository.deletedPhotoUrls)
    }

    @Test
    fun save_whileUploadingPhoto_isSavingButNotWritingProfile() {
        photoRepository.uploadFinished = CompletableDeferred()
        val viewModel = createViewModel()
        viewModel.choosePhoto(CHOSEN_PHOTO_URI)

        viewModel.save()

        assertTrue(viewModel.isSaving)
        assertFalse(viewModel.isWritingProfile)
    }

    @Test
    fun choosePhoto_whileSaving_keepsPhotoBeingUploaded() {
        photoRepository.uploadFinished = CompletableDeferred()
        val viewModel = createViewModel()
        val cameraUri = takeCameraPhoto(viewModel)
        viewModel.save()

        viewModel.choosePhoto(CHOSEN_PHOTO_URI)

        assertEquals(PhotoChange.Replace(cameraUri), viewModel.photoChange)
        assertEquals(listOf(cameraUri), contentUrisInDirectory())
    }

    @Test
    fun removePhoto_whileSaving_keepsPhotoBeingUploaded() {
        photoRepository.uploadFinished = CompletableDeferred()
        val viewModel = createViewModel()
        val cameraUri = takeCameraPhoto(viewModel)
        viewModel.save()

        viewModel.removePhoto()

        assertEquals(PhotoChange.Replace(cameraUri), viewModel.photoChange)
        assertEquals(listOf(cameraUri), contentUrisInDirectory())
    }

    @Test
    fun pictureSaved_whileSaving_keepsPhotoBeingUploadedAndDeletesNewFile() {
        photoRepository.uploadFinished = CompletableDeferred()
        val viewModel = createViewModel()
        val cameraUri = takeCameraPhoto(viewModel)
        openCamera(viewModel)
        viewModel.save()

        viewModel.pictureSaved()

        assertEquals(PhotoChange.Replace(cameraUri), viewModel.photoChange)
        assertEquals(listOf(cameraUri), contentUrisInDirectory())
    }

    @Test
    fun save_whileWritingProfile_isWritingProfile() {
        profileRepository.saveFinished = CompletableDeferred()
        val viewModel = createViewModel()

        viewModel.save()

        assertTrue(viewModel.isWritingProfile)
        assertFalse(viewModel.isDone)
    }

    @Test
    fun save_writeFinishes_stopsWritingProfile() {
        val saveFinished = CompletableDeferred<Unit>()
        profileRepository.saveFinished = saveFinished
        val viewModel = createViewModel()
        viewModel.save()

        saveFinished.complete(Unit)

        assertFalse(viewModel.isWritingProfile)
        assertFalse(viewModel.isSaving)
        assertTrue(viewModel.isDone)
    }

    @Test
    fun save_writeNotConfirmedInTime_isDoneWithoutFailure() {
        profileRepository.saveFinished = CompletableDeferred()
        val viewModel = createViewModel()
        viewModel.save()

        dispatcher.scheduler.advanceTimeBy(PROFILE_WRITE_TIMEOUT_MILLIS + 1)

        assertTrue(viewModel.isDone)
        assertFalse(viewModel.saveFailed)
        assertFalse(viewModel.isWritingProfile)
    }

    @Test
    fun save_writeNotYetTimedOut_staysOpen() {
        profileRepository.saveFinished = CompletableDeferred()
        val viewModel = createViewModel()
        viewModel.save()

        dispatcher.scheduler.advanceTimeBy(PROFILE_WRITE_TIMEOUT_MILLIS - 1)

        assertFalse(viewModel.isDone)
        assertTrue(viewModel.isWritingProfile)
    }

    // Takes a picture up to the camera app opening, and returns the URI it saves into.
    private fun openCamera(viewModel: EditProfileViewModel): String {
        viewModel.takePicture()
        val cameraUri = checkNotNull(viewModel.cameraUriToOpen) { "The camera wasn't asked for" }
        viewModel.cameraOpened()
        return cameraUri
    }

    // Takes a picture that the camera app saves, and returns its URI.
    private fun takeCameraPhoto(viewModel: EditProfileViewModel): String =
        openCamera(viewModel).also { viewModel.pictureSaved() }

    private fun filesInDirectory(): List<File> = cameraDirectory.listFiles().orEmpty().toList()

    private fun contentUrisInDirectory(): List<String> =
        filesInDirectory().map { file -> CAMERA_URI_PREFIX + file.name }

    // A ViewModel that [store] holds, so that clearing [store] clears it.
    private fun createViewModelIn(
        store: ViewModelStore,
        savedStateHandle: SavedStateHandle
    ): EditProfileViewModel {
        val factory = viewModelFactory {
            initializer { createViewModel(savedStateHandle = savedStateHandle) }
        }
        return ViewModelProvider.create(store, factory)[EditProfileViewModel::class]
    }

    private fun createViewModel(
        loggedInUid: String? = profile.uid,
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ): EditProfileViewModel {
        val cameraFileStore = CameraFileStore(
            findDirectory = { cameraDirectory },
            scope = appScope,
            ioDispatcher = dispatcher,
            contentUriOf = { file -> CAMERA_URI_PREFIX + file.name }
        )
        val photo = ChosenPhoto(cameraFileStore, savedStateHandle)
        return EditProfileViewModel(
            profileRepository = profileRepository,
            photoRepository = photoRepository,
            accountRepository = FakeAccountRepository(loggedInUid),
            photo = photo,
            cameraCapture = CameraCapture(
                cameraFileStore = cameraFileStore,
                photo = photo,
                savedStateHandle = savedStateHandle,
                scope = CoroutineScope(dispatcher)
            ),
            savedStateHandle = savedStateHandle
        )
    }

    private class FakeProfileRepository(var profile: UserProfile) : ProfileRepository {
        var failsToRead = false
        var failsToSave = false
        var savedProfile: UserProfile? = null

        // When set, saving waits for it, like a write the server hasn't confirmed yet.
        var saveFinished: CompletableDeferred<Unit>? = null

        override fun getProfile(uid: String): Flow<UserProfile> = if (failsToRead) {
            flow { throw FirebaseException("Could not read the profile") }
        } else {
            flowOf(profile)
        }

        override suspend fun saveProfile(profile: UserProfile) {
            saveFinished?.await()
            if (failsToSave) {
                throw FirebaseException("Could not save the profile")
            }
            savedProfile = profile
        }
    }

    private class FakePhotoRepository : PhotoRepository {
        var failsToUpload = false
        var uploadedPhotoUri: String? = null
        val deletedPhotoUrls = mutableListOf<String>()

        // When set, uploading waits for it, like a slow upload.
        var uploadFinished: CompletableDeferred<Unit>? = null

        override suspend fun uploadPhoto(photoUri: String): String =
            error("Posts aren't edited here")

        override suspend fun uploadProfilePhoto(photoUri: String): String {
            uploadFinished?.await()
            if (failsToUpload) {
                throw PhotoUploadException(IOException("Could not upload the photo"))
            }
            uploadedPhotoUri = photoUri
            return UPLOADED_URL
        }

        override suspend fun deletePhoto(photoUrl: String) {
            deletedPhotoUrls += photoUrl
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
        const val CAMERA_URI_PREFIX = "content://camera/"
    }
}
