@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.post

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.data.CameraFile
import edu.csuci.tweeter.data.CameraFileStore
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.FakeProfileRepository
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PhotoUploadException
import edu.csuci.tweeter.location.GeoPoint
import edu.csuci.tweeter.location.PhotoLocator
import edu.csuci.tweeter.ui.camera.ChosenPhoto
import java.io.File
import java.io.IOException
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
    fun requestPhotoCapture_createsFileAndSetsPendingFile() {
        val viewModel = createViewModel()

        viewModel.requestPhotoCapture()

        val file = checkNotNull(viewModel.pendingCaptureFile)
        assertTrue(viewModel.isCapturing)
        assertFalse(viewModel.captureFailed)
        assertEquals(listOf(file.contentUri), contentUrisInDirectory())
    }

    @Test
    fun requestPhotoCapture_whileAlreadyCapturing_ignoresSecondRequest() {
        val viewModel = createViewModel()
        viewModel.requestPhotoCapture()
        val firstFile = viewModel.pendingCaptureFile

        viewModel.requestPhotoCapture()

        assertEquals(firstFile, viewModel.pendingCaptureFile)
        assertEquals(1, contentUrisInDirectory().size)
    }

    @Test
    fun requestPhotoCapture_fileCreationFails_setsCaptureFailed() {
        val failingFileStore = CameraFileStore(
            findDirectory = { throw IOException("Disk full") },
            scope = appScope,
            ioDispatcher = dispatcher,
            contentUriOf = ::contentUriOf
        )
        val viewModel = createViewModel(cameraFileStore = failingFileStore)

        viewModel.requestPhotoCapture()

        assertTrue(viewModel.captureFailed)
        assertFalse(viewModel.isCapturing)
        assertNull(viewModel.pendingCaptureFile)
    }

    @Test
    fun onPhotoCaptured_makesItThePhotoAndClearsPendingFile() {
        val viewModel = createViewModel()
        val file = startCapture(viewModel)

        viewModel.onPhotoCaptured(file)

        assertEquals(file.contentUri, viewModel.photoUri)
        assertTrue(viewModel.isPhotoFromCamera)
        assertFalse(viewModel.isCapturing)
        assertNull(viewModel.pendingCaptureFile)
    }

    @Test
    fun onCaptureFailed_deletesFileAndKeepsExistingPhoto() {
        val viewModel = createViewModel()
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        val file = startCapture(viewModel)

        viewModel.onCaptureFailed(file)

        assertTrue(viewModel.captureFailed)
        assertFalse(viewModel.isCapturing)
        assertNull(viewModel.pendingCaptureFile)
        assertEquals(CAMERA_ROLL_URI, viewModel.photoUri)
        assertEquals(emptyList<String>(), contentUrisInDirectory())
    }

    @Test
    fun onPhotoCaptured_replacingCameraPhoto_deletesItsFile() {
        val viewModel = createViewModel()
        takeCameraPhoto(viewModel)
        val secondFile = startCapture(viewModel)

        viewModel.onPhotoCaptured(secondFile)

        assertEquals(listOf(secondFile.contentUri), contentUrisInDirectory())
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
    fun choosePhoto_clearsUploadedPhotoUrlAndCaptureFailed() {
        val viewModel = createViewModel(photoRepository = UploadingPhotoRepository())
        takeCameraPhoto(viewModel)
        viewModel.pickLocation(PICKED_POINT)
        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        viewModel.choosePhoto(CAMERA_ROLL_URI)

        assertNull(viewModel.uploadedPhotoUrl)
        assertFalse(viewModel.captureFailed)
    }

    @Test
    fun retakePhoto_clearsPhotoLocationAndDeletesCameraFile() {
        val viewModel = createViewModel(
            photoLocator = PhotoLocator(
                readExif = { null },
                currentDeviceFix = { awaitCancellation() },
                ioDispatcher = dispatcher
            )
        )
        takeCameraPhoto(viewModel)
        viewModel.locatePhoto()

        viewModel.retakePhoto()

        assertNull(viewModel.photoUri)
        assertNull(viewModel.location)
        assertFalse(viewModel.isLocating)
        assertEquals(emptyList<String>(), contentUrisInDirectory())
    }

    @Test
    fun retakePhoto_whileUploading_isIgnored() {
        val viewModel = createViewModel(photoRepository = SlowPhotoRepository())
        val cameraUri = takeCameraPhoto(viewModel)
        viewModel.pickLocation(PICKED_POINT)
        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        viewModel.retakePhoto()

        assertEquals(cameraUri, viewModel.photoUri)
        assertEquals(listOf(cameraUri), contentUrisInDirectory())
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
    fun cleared_whilePendingCapture_discardsUnusedFile() {
        val store = ViewModelStore()
        startCapture(createViewModelIn(store))

        store.clear()

        assertEquals(emptyList<String>(), contentUrisInDirectory())
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
    fun choosePhoto_whileUploading_keepsPhotoBeingUploaded() {
        val viewModel = createViewModel(photoRepository = SlowPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        viewModel.pickLocation(PICKED_POINT)
        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        viewModel.choosePhoto(OTHER_CAMERA_ROLL_URI)

        assertEquals(CAMERA_ROLL_URI, viewModel.photoUri)
    }

    @Test
    fun onPhotoCaptured_whileUploading_keepsPhotoBeingUploadedAndDeletesNewFile() {
        val viewModel = createViewModel(photoRepository = SlowPhotoRepository())
        val cameraUri = takeCameraPhoto(viewModel)
        viewModel.pickLocation(PICKED_POINT)
        val secondFile = startCapture(viewModel)
        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        viewModel.onPhotoCaptured(secondFile)

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

    // Starts a capture, returning the pending CameraFile created by the ViewModel.
    private fun startCapture(viewModel: PostViewModel): CameraFile {
        viewModel.requestPhotoCapture()
        return checkNotNull(viewModel.pendingCaptureFile) { "No capture file was created" }
    }

    // Takes a picture that completes capture, and returns its content URI.
    private fun takeCameraPhoto(viewModel: PostViewModel): String =
        startCapture(viewModel).also { viewModel.onPhotoCaptured(it) }.contentUri

    // A ViewModel that [store] holds, so that clearing [store] clears it.
    private fun createViewModelIn(store: ViewModelStore): PostViewModel {
        val factory = viewModelFactory { initializer { createViewModel() } }
        return ViewModelProvider.create(store, factory)[PostViewModel::class]
    }

    private fun createViewModel(
        photoRepository: PhotoRepository = UnusedPhotoRepository(),
        cameraFileStore: CameraFileStore = CameraFileStore(
            findDirectory = { directory },
            scope = appScope,
            ioDispatcher = dispatcher,
            contentUriOf = ::contentUriOf
        ),
        photoLocator: PhotoLocator<String> = PhotoLocator(
            readExif = { null },
            currentDeviceFix = { null },
            ioDispatcher = dispatcher
        )
    ): PostViewModel {
        val photo = ChosenPhoto(cameraFileStore, savedStateHandle)
        return PostViewModel(
            photoRepository = photoRepository,
            postRepository = postRepository,
            profileRepository = FakeProfileRepository(),
            accountRepository = FakeAccountRepository(initialUid = "uid"),
            photoLocator = photoLocator,
            photo = photo,
            cameraFileStore = cameraFileStore,
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
