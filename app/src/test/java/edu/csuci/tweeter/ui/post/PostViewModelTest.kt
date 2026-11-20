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
import edu.csuci.tweeter.location.LocationFix
import edu.csuci.tweeter.location.LocationSource
import edu.csuci.tweeter.location.PhotoLocator
import edu.csuci.tweeter.ui.camera.ChosenPhoto
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
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
private const val LOGGED_IN_UID = "uid"
private val PICKED_POINT = GeoPoint(latitude = 34.16, longitude = -119.04)
private val PICKED_FIX = LocationFix(PICKED_POINT, LocationSource.MANUAL)
private val EXIF_FIX = LocationFix(
    GeoPoint(latitude = 34.2, longitude = -119.2),
    LocationSource.EXIF
)
private val DEVICE_FIX = LocationFix(
    GeoPoint(latitude = 34.3, longitude = -119.3),
    LocationSource.DEVICE,
    accuracyMeters = 5f
)

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
    fun requestPhotoCapture_createsFileToCaptureInto() {
        val viewModel = createViewModel()

        viewModel.requestPhotoCapture()

        assertEquals(listOf(captureFileOf(viewModel).contentUri), contentUrisInDirectory())
    }

    @Test
    fun requestPhotoCapture_whileAlreadyCapturing_ignoresSecondRequest() {
        val viewModel = createViewModel()
        viewModel.requestPhotoCapture()
        val firstStatus = viewModel.captureStatus

        viewModel.requestPhotoCapture()

        assertEquals(firstStatus, viewModel.captureStatus)
        assertEquals(1, contentUrisInDirectory().size)
    }

    @Test
    fun requestPhotoCapture_fileCreationFails_fails() {
        val viewModel = createViewModel(
            cameraFileStore = cameraFileStoreOn(dispatcher) { throw IOException("Disk full") }
        )

        viewModel.requestPhotoCapture()

        assertEquals(CaptureStatus.Failed, viewModel.captureStatus)
    }

    @Test
    fun requestPhotoCapture_whileUploading_isIgnored() {
        val viewModel = createViewModel(photoRepository = SlowPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        shareChosenPhoto(viewModel)

        viewModel.requestPhotoCapture()

        assertEquals(CaptureStatus.Idle, viewModel.captureStatus)
        assertEquals(emptyList<String>(), contentUrisInDirectory())
    }

    @Test
    fun requestPhotoCapture_afterFailedCapture_capturesAgain() {
        val viewModel = createViewModel()
        viewModel.onCaptureFailed(startCapture(viewModel))

        viewModel.requestPhotoCapture()

        assertTrue(viewModel.captureStatus is CaptureStatus.Capturing)
    }

    @Test
    fun requestPhotoCapture_afterShare_clearsUploadedPhotoUrl() {
        val viewModel = createViewModel(photoRepository = UploadingPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        shareChosenPhoto(viewModel)

        viewModel.requestPhotoCapture()

        assertNull(viewModel.uploadedPhotoUrl)
    }

    @Test
    fun clearMessages_afterFailedCapture_isIdle() {
        val viewModel = createViewModel()
        viewModel.onCaptureFailed(startCapture(viewModel))

        viewModel.clearMessages()

        assertEquals(CaptureStatus.Idle, viewModel.captureStatus)
    }

    @Test
    fun clearMessages_afterShare_clearsUploadedPhotoUrl() {
        val viewModel = createViewModel(photoRepository = UploadingPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        shareChosenPhoto(viewModel)

        viewModel.clearMessages()

        assertNull(viewModel.uploadedPhotoUrl)
    }

    @Test
    fun onPhotoCaptured_makesItThePhotoAndIsIdle() {
        val viewModel = createViewModel()
        val file = startCapture(viewModel)

        viewModel.onPhotoCaptured(file)

        assertEquals(file.contentUri, viewModel.photoUri)
        assertTrue(viewModel.isPhotoFromCamera)
        assertEquals(CaptureStatus.Idle, viewModel.captureStatus)
    }

    @Test
    fun onCaptureFailed_deletesFileAndKeepsExistingPhoto() {
        val viewModel = createViewModel()
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        val file = startCapture(viewModel)

        viewModel.onCaptureFailed(file)

        assertEquals(CaptureStatus.Failed, viewModel.captureStatus)
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
    fun choosePhoto_clearsUploadedPhotoUrl() {
        val viewModel = createViewModel(photoRepository = UploadingPhotoRepository())
        takeCameraPhoto(viewModel)
        viewModel.pickLocation(PICKED_POINT)
        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        viewModel.choosePhoto(CAMERA_ROLL_URI)

        assertNull(viewModel.uploadedPhotoUrl)
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
    fun cancelPhotoCapture_whileFileIsCreated_leavesNoFileToCaptureInto() {
        val ioDispatcher = StandardTestDispatcher(dispatcher.scheduler)
        val viewModel = createViewModel(cameraFileStore = cameraFileStoreOn(ioDispatcher))
        viewModel.requestPhotoCapture()

        viewModel.cancelPhotoCapture()
        ioDispatcher.scheduler.advanceUntilIdle()

        assertEquals(CaptureStatus.Idle, viewModel.captureStatus)
        assertEquals(emptyList<String>(), contentUrisInDirectory())
    }

    @Test
    fun cancelPhotoCapture_whileFileIsCreated_reportsNoFailureOfCreatingIt() {
        val ioDispatcher = StandardTestDispatcher(dispatcher.scheduler)
        val viewModel = createViewModel(
            cameraFileStore = cameraFileStoreOn(ioDispatcher) { throw IOException("Disk full") }
        )
        viewModel.requestPhotoCapture()

        viewModel.cancelPhotoCapture()
        ioDispatcher.scheduler.advanceUntilIdle()

        assertEquals(CaptureStatus.Idle, viewModel.captureStatus)
    }

    @Test
    fun cancelPhotoCapture_whileCapturing_deletesFile() {
        val viewModel = createViewModel()
        startCapture(viewModel)

        viewModel.cancelPhotoCapture()

        assertEquals(CaptureStatus.Idle, viewModel.captureStatus)
        assertEquals(emptyList<String>(), contentUrisInDirectory())
    }

    @Test
    fun requestPhotoCapture_afterCancelWhileFileIsCreated_takesPictureIntoOneFile() {
        val ioDispatcher = StandardTestDispatcher(dispatcher.scheduler)
        val viewModel = createViewModel(cameraFileStore = cameraFileStoreOn(ioDispatcher))
        viewModel.requestPhotoCapture()
        viewModel.cancelPhotoCapture()

        viewModel.requestPhotoCapture()
        ioDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(captureFileOf(viewModel).contentUri), contentUrisInDirectory())
    }

    @Test
    fun onPhotoCaptured_afterCaptureCancelled_choosesNoPhoto() {
        val viewModel = createViewModel()
        val file = startCapture(viewModel)
        viewModel.cancelPhotoCapture()

        viewModel.onPhotoCaptured(file)

        assertNull(viewModel.photoUri)
        assertEquals(emptyList<String>(), contentUrisInDirectory())
    }

    @Test
    fun onCaptureFailed_afterCaptureCancelled_reportsNoFailure() {
        val viewModel = createViewModel()
        val file = startCapture(viewModel)
        viewModel.cancelPhotoCapture()

        viewModel.onCaptureFailed(file)

        assertEquals(CaptureStatus.Idle, viewModel.captureStatus)
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
    fun photoToLocate_noPhoto_isNull() {
        assertNull(createViewModel().photoToLocate)
    }

    @Test
    fun photoToLocate_sameDocumentAfterRetake_isItAgain() {
        val viewModel = createViewModel()
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        viewModel.locationPermissionsRequested()
        viewModel.retakePhoto()

        viewModel.choosePhoto(CAMERA_ROLL_URI)

        assertEquals(CAMERA_ROLL_URI, viewModel.photoToLocate)
    }

    @Test
    fun photoToLocate_sameDocumentAfterShare_isItAgain() {
        val viewModel = createViewModel(photoRepository = UploadingPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        viewModel.locationPermissionsRequested()
        shareChosenPhoto(viewModel)

        viewModel.choosePhoto(CAMERA_ROLL_URI)

        assertEquals(CAMERA_ROLL_URI, viewModel.photoToLocate)
    }

    @Test
    fun locatePhoto_documentWithGpsTags_findsTheirLocation() {
        val viewModel = createViewModel(
            photoLocator = PhotoLocator(
                readExif = { EXIF_FIX },
                currentDeviceFix = { null },
                ioDispatcher = dispatcher
            )
        )
        viewModel.choosePhoto(CAMERA_ROLL_URI)

        viewModel.locatePhoto()

        assertEquals(EXIF_FIX, viewModel.location)
    }

    @Test
    fun locatePhoto_nothingFound_leavesLocationToPick() {
        val viewModel = createViewModel()
        viewModel.choosePhoto(CAMERA_ROLL_URI)

        viewModel.locatePhoto()

        assertNull(viewModel.location)
        assertFalse(viewModel.isLocating)
    }

    @Test
    fun locatePhoto_whileDeviceLooks_isLocating() {
        val viewModel = createViewModel(photoLocator = SlowDevice().photoLocator())
        takeCameraPhoto(viewModel)

        viewModel.locatePhoto()

        assertTrue(viewModel.isLocating)
    }

    @Test
    fun locatePhoto_afterPick_forgetsPickedLocation() {
        val viewModel = createViewModel(photoLocator = SlowDevice().photoLocator())
        takeCameraPhoto(viewModel)
        viewModel.pickLocation(PICKED_POINT)

        viewModel.locatePhoto()

        assertNull(viewModel.location)
    }

    @Test
    fun locatePhoto_noPhoto_isIgnored() {
        val viewModel = createViewModel(photoLocator = SlowDevice().photoLocator())

        viewModel.locatePhoto()

        assertFalse(viewModel.isLocating)
    }

    @Test
    fun pickLocation_setsPickedLocation() {
        val viewModel = createViewModel()
        viewModel.choosePhoto(CAMERA_ROLL_URI)

        viewModel.pickLocation(PICKED_POINT)

        assertEquals(PICKED_FIX, viewModel.location)
    }

    @Test
    fun pickLocation_whileLocating_stopsLooking() {
        val device = SlowDevice()
        val viewModel = createViewModel(photoLocator = device.photoLocator())
        takeCameraPhoto(viewModel)
        viewModel.locatePhoto()

        viewModel.pickLocation(PICKED_POINT)

        assertTrue(device.lookups.single().isCancelled)
        assertFalse(viewModel.isLocating)
    }

    @Test
    fun pickLocation_whileLocating_keepsPickOverDeviceFixFoundLater() {
        val device = SlowDevice()
        val viewModel = createViewModel(photoLocator = device.photoLocator())
        takeCameraPhoto(viewModel)
        viewModel.locatePhoto()
        viewModel.pickLocation(PICKED_POINT)

        device.fix.complete(DEVICE_FIX)

        assertEquals(PICKED_FIX, viewModel.location)
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

    @Test
    fun uploadPhoto_shared_addsPostAtLocation() {
        val viewModel = createViewModel(photoRepository = UploadingPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        viewModel.pickLocation(PICKED_POINT)

        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        assertEquals(PICKED_POINT, firstPostPoint())
    }

    @Test
    fun uploadPhoto_withoutLocation_isIgnored() {
        val viewModel = createViewModel(photoRepository = UploadingPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)

        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        assertEquals(0, postCount())
    }

    @Test
    fun uploadPhoto_uploadRunning_isUploading() {
        val viewModel = createViewModel(photoRepository = SlowPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        viewModel.pickLocation(PICKED_POINT)

        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        assertTrue(viewModel.isUploading)
    }

    @Test
    fun uploadPhoto_whileUploading_isIgnored() {
        val photoRepository = SlowPhotoRepository()
        val viewModel = createViewModel(photoRepository = photoRepository)
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        shareChosenPhoto(viewModel)

        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        assertEquals(1, photoRepository.uploadCount)
    }

    @Test
    fun uploadPhoto_nobodyLoggedIn_fails() {
        val viewModel = createViewModel(
            photoRepository = SlowPhotoRepository(),
            loggedInUid = null
        )
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        viewModel.pickLocation(PICKED_POINT)

        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        assertTrue(viewModel.uploadFailed)
    }

    @Test
    fun uploadPhoto_retryAfterFailure_isNoLongerFailed() {
        val viewModel = createViewModel(photoRepository = FailingOncePhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        shareChosenPhoto(viewModel)

        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)

        assertFalse(viewModel.uploadFailed)
    }

    @Test
    fun uploadFailed_sameDocumentChosenAgain_isTrue() {
        val viewModel = createViewModel(photoRepository = FailingPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        shareChosenPhoto(viewModel)
        viewModel.retakePhoto()

        viewModel.choosePhoto(CAMERA_ROLL_URI)

        assertTrue(viewModel.uploadFailed)
    }

    @Test
    fun init_afterShare_restoresUploadedPhotoUrl() {
        val viewModel = createViewModel(photoRepository = UploadingPhotoRepository())
        viewModel.choosePhoto(CAMERA_ROLL_URI)
        shareChosenPhoto(viewModel)

        val restored = createViewModel()

        assertEquals(UPLOADED_URL, restored.uploadedPhotoUrl)
    }

    // Shares the chosen photo, at the picked point, with no details.
    private fun shareChosenPhoto(viewModel: PostViewModel) {
        viewModel.pickLocation(PICKED_POINT)
        viewModel.uploadPhoto(caption = "", animalType = "", confidence = null)
    }

    // Starts a capture, returning the file the ViewModel created for the picture.
    private fun startCapture(viewModel: PostViewModel): CameraFile {
        viewModel.requestPhotoCapture()
        return captureFileOf(viewModel)
    }

    // The file the running capture's picture goes into.
    private fun captureFileOf(viewModel: PostViewModel): CameraFile {
        val status = viewModel.captureStatus
        check(status is CaptureStatus.Capturing) { "No capture file was created" }
        return status.file
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
        cameraFileStore: CameraFileStore = cameraFileStoreOn(dispatcher),
        photoLocator: PhotoLocator<String> = PhotoLocator(
            readExif = { null },
            currentDeviceFix = { null },
            ioDispatcher = dispatcher
        ),
        loggedInUid: String? = LOGGED_IN_UID
    ): PostViewModel {
        val photo = ChosenPhoto(cameraFileStore, savedStateHandle)
        return PostViewModel(
            photoRepository = photoRepository,
            postRepository = postRepository,
            profileRepository = FakeProfileRepository(),
            accountRepository = FakeAccountRepository(initialUid = loggedInUid),
            photoLocator = photoLocator,
            photo = photo,
            cameraFileStore = cameraFileStore,
            savedStateHandle = savedStateHandle
        )
    }

    // Camera files created in [findDirectory], and deleted, on [ioDispatcher].
    private fun cameraFileStoreOn(
        ioDispatcher: CoroutineDispatcher,
        findDirectory: () -> File = { directory }
    ): CameraFileStore = CameraFileStore(
        findDirectory = findDirectory,
        scope = appScope,
        ioDispatcher = ioDispatcher,
        contentUriOf = ::contentUriOf
    )

    private fun postCount(): Int = runBlocking { postRepository.getPostTable().first().size }

    private fun firstPostPoint(): GeoPoint = runBlocking {
        val table = postRepository.getPostTable().first()
        GeoPoint(latitude = table.latitude(0), longitude = table.longitude(0))
    }

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
        var uploadCount = 0
            private set

        override suspend fun uploadPhoto(photoUri: String): String {
            uploadCount++
            return awaitCancellation()
        }
    }

    // The first upload fails; those after it don't finish while the test runs.
    private class FailingOncePhotoRepository : UnusedPhotoRepository() {
        private var hasFailed = false

        override suspend fun uploadPhoto(photoUri: String): String {
            if (hasFailed) {
                return awaitCancellation()
            }
            hasFailed = true
            throw PhotoUploadException(IOException("Could not read the photo"))
        }
    }

    // A device that finds where it is once [fix] completes, for photos without GPS tags.
    private inner class SlowDevice {
        val fix = CompletableDeferred<LocationFix?>()

        private val startedLookups = mutableListOf<Job>()

        // The jobs of the lookups started so far, oldest first.
        val lookups: List<Job>
            get() = startedLookups

        fun photoLocator(): PhotoLocator<String> = PhotoLocator(
            readExif = { null },
            currentDeviceFix = ::findCurrentFix,
            ioDispatcher = dispatcher
        )

        private suspend fun findCurrentFix(): LocationFix? {
            startedLookups += currentCoroutineContext().job
            return fix.await()
        }
    }
}
