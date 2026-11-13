@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.camera

import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.CameraFile
import edu.csuci.tweeter.data.CameraFileStore
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
private const val CAMERA_ROLL_URI = "content://media/1"

class ChosenPhotoTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory: File
        get() = File(temporaryFolder.root, "photos")

    private val savedStateHandle = SavedStateHandle()

    @Test
    fun chooseFromCameraRoll_makesItThePhoto() = runTest {
        val photo = chosenPhoto()

        photo.chooseFromCameraRoll(CAMERA_ROLL_URI)

        assertEquals(CAMERA_ROLL_URI, photo.uri)
        assertFalse(photo.isFromCamera)
    }

    @Test
    fun chooseCameraPicture_makesItThePhoto() = runTest {
        val photo = chosenPhoto()
        val picture = cameraPicture()

        photo.chooseCameraPicture(picture)

        assertEquals(picture.contentUri, photo.uri)
        assertTrue(photo.isFromCamera)
    }

    @Test
    fun chooseFromCameraRoll_replacingCameraPicture_deletesItsFile() = runTest {
        val photo = chosenPhoto()
        photo.chooseCameraPicture(cameraPicture())

        photo.chooseFromCameraRoll(CAMERA_ROLL_URI)

        assertEquals(emptyList<File>(), filesInDirectory())
    }

    @Test
    fun chooseCameraPicture_replacingCameraPicture_deletesOnlyReplacedFile() = runTest {
        val photo = chosenPhoto()
        photo.chooseCameraPicture(cameraPicture())
        val newPicture = cameraPicture()

        photo.chooseCameraPicture(newPicture)

        assertEquals(listOf(File(newPicture.path)), filesInDirectory())
    }

    @Test
    fun clear_afterCameraPicture_deletesItsFileAndLeavesNoPhoto() = runTest {
        val photo = chosenPhoto()
        photo.chooseCameraPicture(cameraPicture())

        photo.clear()

        assertNull(photo.uri)
        assertEquals(emptyList<File>(), filesInDirectory())
    }

    @Test
    fun chooseFromCameraRoll_whileLocked_keepsPhoto() = runTest {
        val photo = chosenPhoto()
        val picture = cameraPicture()
        photo.chooseCameraPicture(picture)
        photo.lock()

        photo.chooseFromCameraRoll(CAMERA_ROLL_URI)

        assertEquals(picture.contentUri, photo.uri)
        assertEquals(listOf(File(picture.path)), filesInDirectory())
    }

    @Test
    fun chooseCameraPicture_whileLocked_keepsPhotoAndDeletesNewFile() = runTest {
        val photo = chosenPhoto()
        photo.chooseFromCameraRoll(CAMERA_ROLL_URI)
        photo.lock()

        photo.chooseCameraPicture(cameraPicture())

        assertEquals(CAMERA_ROLL_URI, photo.uri)
        assertEquals(emptyList<File>(), filesInDirectory())
    }

    @Test
    fun chooseFromCameraRoll_afterUnlock_replacesPhoto() = runTest {
        val photo = chosenPhoto()
        photo.lock()
        photo.unlock()

        photo.chooseFromCameraRoll(CAMERA_ROLL_URI)

        assertEquals(CAMERA_ROLL_URI, photo.uri)
    }

    @Test
    fun clear_whileLocked_leavesNoPhoto() = runTest {
        val photo = chosenPhoto()
        photo.chooseFromCameraRoll(CAMERA_ROLL_URI)
        photo.lock()

        photo.clear()

        assertNull(photo.uri)
    }

    @Test
    fun restored_keepsCameraPicture() = runTest {
        val picture = cameraPicture()
        chosenPhoto().chooseCameraPicture(picture)

        val restored = chosenPhoto()

        assertEquals(picture.contentUri, restored.uri)
        assertTrue(restored.isFromCamera)
    }

    private fun TestScope.cameraFileStore(): CameraFileStore = CameraFileStore(
        findDirectory = { directory },
        scope = backgroundScope,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        contentUriOf = { file -> CONTENT_URI_PREFIX + file.name }
    )

    private fun TestScope.chosenPhoto(): ChosenPhoto = ChosenPhoto(
        cameraFileStore = cameraFileStore(),
        savedStateHandle = savedStateHandle
    )

    // A file the camera app saved a picture into.
    private suspend fun TestScope.cameraPicture(): CameraFile = cameraFileStore().create()

    private fun filesInDirectory(): List<File> = directory.listFiles().orEmpty().toList()
}
