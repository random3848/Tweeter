@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.camera

import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.CameraFileStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val CONTENT_URI_PREFIX = "content://camera/"
private const val CAMERA_ROLL_URI = "content://media/1"

class CameraCaptureTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory: File
        get() = File(temporaryFolder.root, "photos")

    private val savedStateHandle = SavedStateHandle()

    @Test
    fun takePicture_asksToOpenCameraWithNewFile() = runTest {
        val capture = cameraCapture()

        capture.takePicture()

        assertEquals(listOf(capture.cameraUriToOpen), contentUrisInDirectory())
        assertTrue(capture.isOpeningCamera)
    }

    @Test
    fun takePicture_whileOpeningCamera_createsNoOtherFile() = runTest {
        val capture = cameraCapture()
        capture.takePicture()

        capture.takePicture()

        assertEquals(1, filesInDirectory().size)
    }

    @Test
    fun takePicture_fileCantBeCreated_failsWithoutOpeningCamera() = runTest {
        directory.writeText("A file where the directory should be")
        val capture = cameraCapture()

        capture.takePicture()

        assertTrue(capture.cameraFailed)
        assertNull(capture.cameraUriToOpen)
        assertFalse(capture.isOpeningCamera)
    }

    @Test
    fun takePicture_afterFailure_clearsFailure() = runTest {
        val capture = cameraCapture()
        capture.takePicture()
        capture.cameraUnavailable()

        capture.takePicture()

        assertFalse(capture.cameraFailed)
    }

    @Test
    fun takePicture_cameraNeverAnswered_replacesItsFile() = runTest {
        val capture = cameraCapture()
        openCamera(capture)

        capture.takePicture()

        assertEquals(listOf(capture.cameraUriToOpen), contentUrisInDirectory())
    }

    @Test
    fun dismissCameraFailure_clearsFailure() = runTest {
        val capture = cameraCapture()
        capture.takePicture()
        capture.cameraUnavailable()

        capture.dismissCameraFailure()

        assertFalse(capture.cameraFailed)
    }

    @Test
    fun cameraOpened_stopsOpeningCamera() = runTest {
        val capture = cameraCapture()
        capture.takePicture()

        capture.cameraOpened()

        assertNull(capture.cameraUriToOpen)
        assertFalse(capture.isOpeningCamera)
    }

    @Test
    fun cameraUnavailable_deletesFileAndFails() = runTest {
        val capture = cameraCapture()
        capture.takePicture()

        capture.cameraUnavailable()

        assertEquals(emptyList<File>(), filesInDirectory())
        assertNull(capture.cameraUriToOpen)
        assertFalse(capture.isOpeningCamera)
        assertTrue(capture.cameraFailed)
    }

    @Test
    fun pictureSaved_makesPictureThePhoto() = runTest {
        val photo = chosenPhoto()
        val capture = cameraCapture(photo)
        val cameraUri = openCamera(capture)

        capture.pictureSaved()

        assertEquals(cameraUri, photo.uri)
        assertTrue(photo.isFromCamera)
    }

    @Test
    fun pictureSaved_noPictureTaken_keepsPhoto() = runTest {
        val photo = chosenPhoto()
        photo.chooseFromCameraRoll(CAMERA_ROLL_URI)

        cameraCapture(photo).pictureSaved()

        assertEquals(CAMERA_ROLL_URI, photo.uri)
    }

    @Test
    fun pictureSaved_answeredTwice_keepsSavedPicture() = runTest {
        val photo = chosenPhoto()
        val capture = cameraCapture(photo)
        val cameraUri = openCamera(capture)
        capture.pictureSaved()

        capture.pictureSaved()

        assertEquals(cameraUri, photo.uri)
        assertEquals(listOf(cameraUri), contentUrisInDirectory())
    }

    @Test
    fun pictureCancelled_deletesFileAndKeepsPhoto() = runTest {
        val photo = chosenPhoto()
        photo.chooseFromCameraRoll(CAMERA_ROLL_URI)
        val capture = cameraCapture(photo)
        openCamera(capture)

        capture.pictureCancelled()

        assertEquals(CAMERA_ROLL_URI, photo.uri)
        assertEquals(emptyList<File>(), filesInDirectory())
    }

    @Test
    fun stopOpeningCamera_beforeFileCreated_createsNone() = runTest {
        val capture = cameraCapture(ioDispatcher = StandardTestDispatcher(testScheduler))
        capture.takePicture()

        capture.stopOpeningCamera()
        runCurrent()

        assertEquals(emptyList<File>(), filesInDirectory())
        assertNull(capture.cameraUriToOpen)
        assertFalse(capture.isOpeningCamera)
    }

    @Test
    fun stopOpeningCamera_fileCreated_deletesItAndStopsAsking() = runTest {
        val capture = cameraCapture()
        capture.takePicture()

        capture.stopOpeningCamera()

        assertEquals(emptyList<File>(), filesInDirectory())
        assertNull(capture.cameraUriToOpen)
        assertFalse(capture.isOpeningCamera)
    }

    @Test
    fun stopOpeningCamera_cameraOpen_stillTakesItsPicture() = runTest {
        val photo = chosenPhoto()
        val capture = cameraCapture(photo)
        val cameraUri = openCamera(capture)

        capture.stopOpeningCamera()
        capture.pictureSaved()

        assertEquals(cameraUri, photo.uri)
    }

    @Test
    fun close_cameraOpen_keepsFileForRestoredCapture() = runTest {
        val capture = cameraCapture()
        openCamera(capture)

        capture.close()

        assertEquals(1, filesInDirectory().size)
    }

    @Test
    fun close_beforeCameraOpened_deletesFile() = runTest {
        val capture = cameraCapture()
        capture.takePicture()

        capture.close()

        assertEquals(emptyList<File>(), filesInDirectory())
    }

    @Test
    fun restored_whileCameraOpen_takesItsPicture() = runTest {
        val cameraUri = openCamera(cameraCapture())
        val restoredPhoto = chosenPhoto()
        val restored = cameraCapture(restoredPhoto)

        restored.pictureSaved()

        assertEquals(cameraUri, restoredPhoto.uri)
    }

    @Test
    fun restored_beforeCameraOpened_doesNotOpenIt() = runTest {
        cameraCapture().takePicture()

        val restored = cameraCapture()

        assertNull(restored.cameraUriToOpen)
        assertFalse(restored.isOpeningCamera)
    }

    // Takes a picture up to the camera app opening, and returns the URI it saves into.
    private fun openCamera(capture: CameraCapture): String {
        capture.takePicture()
        val cameraUri = checkNotNull(capture.cameraUriToOpen) { "The camera wasn't asked for" }
        capture.cameraOpened()
        return cameraUri
    }

    private fun TestScope.cameraFileStore(
        ioDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler)
    ): CameraFileStore = CameraFileStore(
        findDirectory = { directory },
        scope = backgroundScope,
        ioDispatcher = ioDispatcher,
        contentUriOf = ::contentUriOf
    )

    private fun TestScope.chosenPhoto(): ChosenPhoto = ChosenPhoto(
        cameraFileStore = cameraFileStore(),
        savedStateHandle = savedStateHandle
    )

    private fun TestScope.cameraCapture(
        photo: ChosenPhoto = chosenPhoto(),
        ioDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler)
    ): CameraCapture = CameraCapture(
        cameraFileStore = cameraFileStore(ioDispatcher),
        photo = photo,
        savedStateHandle = savedStateHandle,
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    )

    private fun contentUriOf(file: File): String = CONTENT_URI_PREFIX + file.name

    private fun filesInDirectory(): List<File> = directory.listFiles().orEmpty().toList()

    private fun contentUrisInDirectory(): List<String> = filesInDirectory().map(::contentUriOf)
}
