@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.camera

import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.CameraFiles
import java.io.File
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
    fun start_asksToOpenCameraWithNewFile() = runTest {
        val capture = cameraCapture()

        capture.start()

        assertEquals(listOf(capture.uriToOpen), contentUrisInDirectory())
        assertTrue(capture.isTakingPicture)
    }

    @Test
    fun start_whileTakingPicture_createsNoOtherFile() = runTest {
        val capture = cameraCapture()
        capture.start()

        capture.start()

        assertEquals(1, filesInDirectory().size)
    }

    @Test
    fun start_fileCantBeCreated_failsWithoutOpeningCamera() = runTest {
        directory.writeText("A file where the directory should be")
        val capture = cameraCapture()

        capture.start()

        assertTrue(capture.failed)
        assertNull(capture.uriToOpen)
        assertFalse(capture.isTakingPicture)
    }

    @Test
    fun start_afterFailure_clearsFailure() = runTest {
        val capture = cameraCapture()
        capture.start()
        capture.cameraUnavailable()

        capture.start()

        assertFalse(capture.failed)
    }

    @Test
    fun dismissFailure_clearsFailure() = runTest {
        val capture = cameraCapture()
        capture.start()
        capture.cameraUnavailable()

        capture.dismissFailure()

        assertFalse(capture.failed)
    }

    @Test
    fun cameraOpened_stopsAskingToOpenCamera() = runTest {
        val capture = cameraCapture()
        capture.start()

        capture.cameraOpened()

        assertNull(capture.uriToOpen)
        assertTrue(capture.isTakingPicture)
    }

    @Test
    fun keep_returnsPictureFileAndFinishes() = runTest {
        val capture = cameraCapture()
        capture.start()
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
        capture.start()
        capture.cameraOpened()

        capture.discard()

        assertEquals(emptyList<File>(), filesInDirectory())
        assertFalse(capture.isTakingPicture)
    }

    @Test
    fun cameraUnavailable_deletesFileAndFails() = runTest {
        val capture = cameraCapture()
        capture.start()

        capture.cameraUnavailable()

        assertEquals(emptyList<File>(), filesInDirectory())
        assertNull(capture.uriToOpen)
        assertFalse(capture.isTakingPicture)
        assertTrue(capture.failed)
    }

    @Test
    fun restored_whileCameraOpen_keepsPictureFile() = runTest {
        val capture = cameraCapture()
        capture.start()
        capture.cameraOpened()

        val restored = cameraCapture()

        assertNull(restored.uriToOpen)
        assertEquals(contentUrisInDirectory(), listOfNotNull(restored.keep()?.contentUri))
    }

    @Test
    fun restored_beforeCameraOpened_stillAsksToOpenIt() = runTest {
        val capture = cameraCapture()
        capture.start()

        val restored = cameraCapture()

        assertEquals(capture.uriToOpen, restored.uriToOpen)
    }

    private fun TestScope.cameraCapture(): CameraCapture = CameraCapture(
        cameraFiles = CameraFiles(
            directory = directory,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            contentUriOf = ::contentUriOf
        ),
        savedStateHandle = savedStateHandle
    )

    private fun contentUriOf(file: File): String = CONTENT_URI_PREFIX + file.name

    private fun filesInDirectory(): List<File> = directory.listFiles().orEmpty().toList()

    private fun contentUrisInDirectory(): List<String> = filesInDirectory().map(::contentUriOf)
}
