@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.data

import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val CONTENT_URI_PREFIX = "content://camera/"

class CameraFilesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory: File
        get() = File(temporaryFolder.root, "photos")

    @Test
    fun create_createsEmptyFileInDirectory() = runTest {
        val cameraFile = cameraFiles().create()

        val file = File(cameraFile.path)
        assertTrue(file.isFile)
        assertEquals(0L, file.length())
        assertEquals(directory, file.parentFile)
    }

    @Test
    fun create_returnsContentUriOfFile() = runTest {
        val cameraFile = cameraFiles().create()

        assertEquals(contentUriOf(File(cameraFile.path)), cameraFile.contentUri)
    }

    @Test
    fun create_twice_createsDifferentFiles() = runTest {
        val files = cameraFiles()

        assertNotEquals(files.create().path, files.create().path)
    }

    @Test
    fun create_directoryCantBeCreated_throwsIoException() = runTest {
        directory.writeText("A file where the directory should be")

        val error = runCatching { cameraFiles().create() }.exceptionOrNull()

        assertEquals(IOException::class, error?.let { it::class })
    }

    @Test
    fun create_noContentUri_deletesFileAndRethrows() = runTest {
        val files = CameraFiles(
            directory = directory,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            contentUriOf = { throw IllegalArgumentException("Not a shared folder") }
        )

        val error = runCatching { files.create() }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class, error?.let { it::class })
        assertEquals(emptyList<File>(), directory.listFiles().orEmpty().toList())
    }

    @Test
    fun delete_deletesFile() = runTest {
        val files = cameraFiles()
        val cameraFile = files.create()

        files.delete(cameraFile.path)

        assertFalse(File(cameraFile.path).exists())
    }

    @Test
    fun delete_fileAlreadyGone_doesNothing() = runTest {
        val files = cameraFiles()
        val cameraFile = files.create()
        files.delete(cameraFile.path)

        files.delete(cameraFile.path)

        assertFalse(File(cameraFile.path).exists())
    }

    private fun TestScope.cameraFiles(): CameraFiles = CameraFiles(
        directory = directory,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        contentUriOf = ::contentUriOf
    )

    private fun contentUriOf(file: File): String = CONTENT_URI_PREFIX + file.name
}
