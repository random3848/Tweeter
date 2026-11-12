@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.post

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.data.CameraFileStore
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.FakeProfileRepository
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PhotoUploadException
import edu.csuci.tweeter.location.GeoPoint
import edu.csuci.tweeter.location.PhotoLocator
import edu.csuci.tweeter.ui.camera.CameraCapture
import edu.csuci.tweeter.ui.camera.ChosenPhoto
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

private const val CONTENT_URI_PREFIX = "content://camera/"
private const val CAMERA_ROLL_URI = "content://media/1"
private const val OTHER_CAMERA_ROLL_URI = "content://media/2"
private const val UPLOADED_URL = "https://example.com/uploaded.jpg"
private val PICKED_POINT = GeoPoint(latitude = 34.16, longitude = -119.04)

class PostViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    // Outlives the ViewModels, as the app's scope does, to delete camera photos they discard.
    private val appScope = TestScope(dispatcher)

    private val directory: File
        get() = File(temporaryFolder.root, "photos")

    private val savedStateHandle = SavedStateHandle()

    private val postRepository = FakePostRepository(initialPosts = emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun takePicture_asksToOpenCameraWithNewFile() {
        val viewModel = createViewModel()

        viewModel.takePicture()

        assertEquals(listOf(viewModel.cameraUriToOpen), contentUrisInDirectory())
        assertTrue(viewModel.isOpeningCamera)
    }

    @Test
    fun pictureSaved_makesItThePhoto() {
        val viewModel = createViewModel()
        val cameraUri = openCamera(viewModel)

        viewModel.pictureSaved()

        assertEquals(cameraUri, viewModel.photoUri)
        assertTrue(viewModel.isPhotoFromCamera)
    }

    @Test
    fun pictureCancelled_deletesFileAndKeepsPhoto() {
        val viewModel = createViewModel()
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        openCamera(viewModel)

        viewModel.pictureCancelled()

        assertEquals(CAMERA_ROLL_URI, viewModel.photoUri)
        assertEquals(emptyList<String>(), contentUrisInDirectory())
    }

    @Test
    fun pictureSaved_replacingCameraPhoto_deletesItsFile() {
        val viewModel = createViewModel()
        takeCameraPhoto(viewModel)
        val secondUri = openCamera(viewModel)

        viewModel.pictureSaved()

        assertEquals(listOf(secondUri), contentUrisInDirectory())
    }

    @Test
    fun choosePhoto_replacingCameraPhoto_deletesItsFile() {
        val viewModel = createViewModel()
        takeCameraPhoto(viewModel)

        viewModel.choosePhoto(CAMERA_ROLL_URI)

        assertEquals(CAMERA_ROLL_URI, viewModel.photoUri)
        assertFalse(viewModel.isPhotoFromCamera)
        assertEquals(emptyList<String>(), contentUrisInDirectory())
    }

    @Test
    fun cameraUnavailable_failsAndDeletesFile() {
        val viewModel = createViewModel()
        viewModel.takePicture()

        viewModel.cameraUnavailable()

        assertTrue(viewModel.cameraFailed)
        assertFalse(viewModel.isOpeningCamera)
        assertEquals(emptyList<String>(), contentUrisInDirectory())
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
    fun init_savedPhoto_restoresIt() {
        val cameraUri = takeCameraPhoto(createViewModel())

        val restored = createViewModel()

        assertEquals(cameraUri, restored.photoUri)
        assertTrue(restored.isPhotoFromCamera)
    }

    @Test
    fun init_noPhoto_hasNone() {
        assertNull(createViewModel().photoUri)
    }

    @Test
    fun cleared_afterCameraPhoto_restoredViewModelKeepsIt() {
        val store = ViewModelStore()
        val cameraUri = takeCameraPhoto(createViewModelIn(store))

        store.clear()
        val restored = createViewModel()

        assertEquals(cameraUri, restored.photoUri)
        assertEquals(listOf(cameraUri), contentUrisInDirectory())
    }

    @Test
    fun cleared_whileCameraOpen_restoredViewModelKeepsPicture() {
        val store = ViewModelStore()
        val cameraUri = openCamera(createViewModelIn(store))

        store.clear()
        val restored = createViewModel()
        restored.pictureSaved()

        assertEquals(cameraUri, restored.photoUri)
        assertEquals(listOf(cameraUri), contentUrisInDirectory())
    }

    @Test
    fun photoToLocate_newPhoto_isIt() {
        val viewModel = createViewModel()

        viewModel.choosePhoto(CAMERA_ROLL_URI)

        assertEquals(CAMERA_ROLL_URI, viewModel.photoToLocate)
    }

    @Test
    fun photoToLocate_permissionsRequested_isNull() {
        val viewModel = createViewModel()
        viewModel.choosePhoto(CAMERA_ROLL_URI)

        viewModel.locationPermissionsRequested()

        assertNull(viewModel.photoToLocate)
    }

    @Test
    fun photoToLocate_cameraAnswersWithoutPicture_staysNull() {
        val viewModel = createViewModel()
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        viewModel.locationPermissionsRequested()

        viewModel.pictureSaved()

        assertNull(viewModel.photoToLocate)
    }

    @Test
    fun choosePhoto_whileUploading_keepsPhotoBeingUploaded() {
        val viewModel = createViewModel(photoRepository = SlowPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        viewModel.pickLocation(PICKED_POINT)
        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        viewModel.choosePhoto(OTHER_CAMERA_ROLL_URI)

        assertEquals(CAMERA_ROLL_URI, viewModel.photoUri)
    }

    @Test
    fun pictureSaved_whileUploading_keepsPhotoBeingUploadedAndDeletesNewFile() {
        val viewModel = createViewModel(photoRepository = SlowPhotoRepository())
        val cameraUri = takeCameraPhoto(viewModel)
        viewModel.pickLocation(PICKED_POINT)
        openCamera(viewModel)
        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        viewModel.pictureSaved()

        assertEquals(cameraUri, viewModel.photoUri)
        assertEquals(listOf(cameraUri), contentUrisInDirectory())
    }

    @Test
    fun uploadPhoto_uploadFails_failsWithoutPost() {
        val viewModel = createViewModel(photoRepository = FailingPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        viewModel.pickLocation(PICKED_POINT)

        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        assertTrue(viewModel.uploadFailed)
        assertFalse(viewModel.isUploading)
        assertEquals(0, postCount())
    }

    @Test
    fun uploadFailed_newPhotoChosen_isFalse() {
        val viewModel = createViewModel(photoRepository = FailingPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        viewModel.pickLocation(PICKED_POINT)
        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        viewModel.choosePhoto(OTHER_CAMERA_ROLL_URI)

        assertFalse(viewModel.uploadFailed)
    }

    @Test
    fun uploadPhoto_shared_forgetsCameraPhotoAndKeepsItsUrl() {
        val viewModel = createViewModel(photoRepository = UploadingPhotoRepository())
        takeCameraPhoto(viewModel)
        viewModel.pickLocation(PICKED_POINT)

        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        assertNull(viewModel.photoUri)
        assertEquals(UPLOADED_URL, viewModel.uploadedPhotoUrl)
        assertEquals(emptyList<String>(), contentUrisInDirectory())
    }

    // Takes a picture up to the camera app opening, and returns the URI it saves into.
    private fun openCamera(viewModel: PostViewModel): String {
        viewModel.takePicture()
        val cameraUri = checkNotNull(viewModel.cameraUriToOpen) { "The camera wasn't asked for" }
        viewModel.cameraOpened()
        return cameraUri
    }

    // Takes a picture that the camera app saves, and returns its URI.
    private fun takeCameraPhoto(viewModel: PostViewModel): String =
        openCamera(viewModel).also { viewModel.pictureSaved() }

    // A ViewModel that [store] holds, so that clearing [store] clears it.
    private fun createViewModelIn(store: ViewModelStore): PostViewModel {
        val factory = viewModelFactory { initializer { createViewModel() } }
        return ViewModelProvider.create(store, factory)[PostViewModel::class]
    }

    private fun createViewModel(
        photoRepository: PhotoRepository = UnusedPhotoRepository()
    ): PostViewModel {
        val cameraFileStore = CameraFileStore(
            findDirectory = { directory },
            scope = appScope,
            ioDispatcher = dispatcher,
            contentUriOf = ::contentUriOf
        )
        val photo = ChosenPhoto(cameraFileStore, savedStateHandle)
        return PostViewModel(
            photoRepository = photoRepository,
            postRepository = postRepository,
            profileRepository = FakeProfileRepository(),
            accountRepository = FakeAccountRepository(initialUid = "uid"),
            photoLocator = PhotoLocator(
                readExif = { null },
                currentDeviceFix = { null },
                ioDispatcher = dispatcher
            ),
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

    private fun postCount(): Int = runBlocking { postRepository.getPostTable().first().size }

    private fun contentUriOf(file: File): String = CONTENT_URI_PREFIX + file.name

    private fun contentUrisInDirectory(): List<String> =
        directory.listFiles().orEmpty().map(::contentUriOf)

    // For tests that don't upload, so nothing reaches Storage.
    private open class UnusedPhotoRepository : PhotoRepository {
        override suspend fun uploadPhoto(photoUri: String): String =
            error("Not uploaded in these tests")

        override suspend fun uploadProfilePhoto(photoUri: String): String =
            error("Not uploaded in these tests")

        override suspend fun deletePhoto(photoUrl: String) = error("Not deleted in these tests")
    }

    private class FailingPhotoRepository : UnusedPhotoRepository() {
        override suspend fun uploadPhoto(photoUri: String): String =
            throw PhotoUploadException(IOException("Could not read the photo"))
    }

    private class UploadingPhotoRepository : UnusedPhotoRepository() {
        override suspend fun uploadPhoto(photoUri: String): String = UPLOADED_URL
    }

    // An upload that doesn't finish while the test runs, like a slow one.
    private class SlowPhotoRepository : UnusedPhotoRepository() {
        override suspend fun uploadPhoto(photoUri: String): String = awaitCancellation()
    }
}
