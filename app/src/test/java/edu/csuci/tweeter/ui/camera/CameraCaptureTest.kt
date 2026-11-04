@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.camera

import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.CameraFiles
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val CONTENT_URI_PREFIX = "content://camera/"

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
        assertTrue(capture.isTakingPicture)
    }

    @Test
    fun takePicture_whileTakingPicture_createsNoOtherFile() = runTest {
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
        assertFalse(capture.isTakingPicture)
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
    fun dismissCameraFailure_clearsFailure() = runTest {
        val capture = cameraCapture()
        capture.takePicture()
        capture.cameraUnavailable()

        capture.dismissCameraFailure()

        assertFalse(capture.cameraFailed)
    }

    @Test
    fun cameraOpened_stopsAskingToOpenCamera() = runTest {
        val capture = cameraCapture()
        capture.takePicture()

        capture.cameraOpened()

        assertNull(capture.cameraUriToOpen)
        assertTrue(capture.isTakingPicture)
    }

    @Test
    fun keep_returnsPictureFileAndFinishes() = runTest {
        val capture = cameraCapture()
        capture.takePicture()
        capture.cameraOpened()

        val picture = capture.keep()

        assertEquals(contentUrisInDirectory(), listOfNotNull(picture?.contentUri))
        assertTrue(File(checkNotNull(picture).path).exists())
        assertFalse(capture.isTakingPicture)
    }

    @Test
    fun keep_noPictureBeingTaken_returnsNull() = runTest {
        assertNull(cameraCapture().keep())
    }

    @Test
    fun discard_deletesFileAndFinishes() = runTest {
        val capture = cameraCapture()
        capture.takePicture()
        capture.cameraOpened()

        capture.discard()

        assertEquals(emptyList<File>(), filesInDirectory())
        assertFalse(capture.isTakingPicture)
    }

    @Test
    fun cameraUnavailable_deletesFileAndFails() = runTest {
        val capture = cameraCapture()
        capture.takePicture()

        capture.cameraUnavailable()

        assertEquals(emptyList<File>(), filesInDirectory())
        assertNull(capture.cameraUriToOpen)
        assertFalse(capture.isTakingPicture)
        assertTrue(capture.cameraFailed)
    }

    @Test
    fun close_whileTakingPicture_deletesFile() = runTest {
        val capture = cameraCapture()
        capture.takePicture()
        capture.cameraOpened()

        capture.close()

        assertEquals(emptyList<File>(), filesInDirectory())
    }

    @Test
    fun restored_whileCameraOpen_keepsPictureFile() = runTest {
        val capture = cameraCapture()
        capture.takePicture()
        capture.cameraOpened()

        val restored = cameraCapture()

        assertNull(restored.cameraUriToOpen)
        assertEquals(contentUrisInDirectory(), listOfNotNull(restored.keep()?.contentUri))
    }

    @Test
    fun restored_beforeCameraOpened_stillAsksToOpenIt() = runTest {
        val capture = cameraCapture()
        capture.takePicture()

        val restored = cameraCapture()

        assertEquals(capture.cameraUriToOpen, restored.cameraUriToOpen)
    }

    private fun TestScope.cameraCapture(): CameraCapture = CameraCapture(
        cameraFiles = CameraFiles(
            directory = directory,
            scope = backgroundScope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            contentUriOf = ::contentUriOf
        ),
        savedStateHandle = savedStateHandle,
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    )

    private fun contentUriOf(file: File): String = CONTENT_URI_PREFIX + file.name

    private fun filesInDirectory(): List<File> = directory.listFiles().orEmpty().toList()

    private fun contentUrisInDirectory(): List<String> = filesInDirectory().map(::contentUriOf)
}
