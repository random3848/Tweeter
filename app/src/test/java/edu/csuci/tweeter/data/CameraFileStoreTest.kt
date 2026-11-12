@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.data

import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val CONTENT_URI_PREFIX = "content://camera/"

class CameraFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory: File
        get() = File(temporaryFolder.root, "photos")

    @Test
    fun create_createsEmptyFileInDirectory() = runTest {
        val cameraFile = cameraFileStore().create()

        val file = File(cameraFile.path)
        assertTrue(file.isFile)
        assertEquals(0L, file.length())
        assertEquals(directory, file.parentFile)
    }

    @Test
    fun create_returnsContentUriOfFile() = runTest {
        val cameraFile = cameraFileStore().create()

        assertEquals(contentUriOf(File(cameraFile.path)), cameraFile.contentUri)
    }

    @Test
    fun create_twice_createsDifferentFiles() = runTest {
        val store = cameraFileStore()

        assertNotEquals(store.create().path, store.create().path)
    }

    @Test
    fun create_directoryCantBeCreated_throwsIoException() = runTest {
        directory.writeText("A file where the directory should be")

        val error = runCatching { cameraFileStore().create() }.exceptionOrNull()

        assertEquals(IOException::class, error?.let { it::class })
    }

    @Test
    fun create_noContentUri_deletesFileAndRethrows() = runTest {
        val store = CameraFileStore(
            findDirectory = { directory },
            scope = backgroundScope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            contentUriOf = { throw IllegalArgumentException("Not a shared folder") }
        )

        val error = runCatching { store.create() }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class, error?.let { it::class })
        assertEquals(emptyList<File>(), directory.listFiles().orEmpty().toList())
    }

    @Test
    fun create_findsDirectoryOnIoDispatcher() = runTest {
        var ioThread: Thread? = null
        var findingThread: Thread? = null
        val ioDispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(task).also { ioThread = it }
        }.asCoroutineDispatcher()
        val store = CameraFileStore(
            findDirectory = {
                findingThread = Thread.currentThread()
                directory
            },
            scope = backgroundScope,
            ioDispatcher = ioDispatcher,
            contentUriOf = ::contentUriOf
        )

        ioDispatcher.use { store.create() }

        assertSame(ioThread, findingThread)
    }

    @Test
    fun discard_deletesFileOnIoDispatcher() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val store = cameraFileStore(ioDispatcher)
        val cameraFile = store.create()

        store.discard(cameraFile.path)
        val existsUntilIoRuns = File(cameraFile.path).exists()
        runCurrent()

        assertTrue(existsUntilIoRuns)
        assertFalse(File(cameraFile.path).exists())
    }

    @Test
    fun discard_fileAlreadyGone_doesNothing() = runTest {
        val store = cameraFileStore()
        val cameraFile = store.create()
        store.discard(cameraFile.path)

        store.discard(cameraFile.path)
        runCurrent()

        assertFalse(File(cameraFile.path).exists())
    }

    @Test
    fun discardAll_deletesEveryFile() = runTest {
        val store = cameraFileStore()
        store.create()
        store.create()

        store.discardAll()
        runCurrent()

        assertEquals(emptyList<File>(), directory.listFiles().orEmpty().toList())
    }

    @Test
    fun create_whileDiscardingAll_keepsNewFile() = runBlocking {
        val listingAllowed = CountDownLatch(1)
        // Lists its files only once allowed, so that discarding them waits there meanwhile.
        val slowToList = object : File(directory.path) {
            override fun listFiles(): Array<File>? {
                listingAllowed.await()
                return super.listFiles()
            }
        }
        val discardScope = CoroutineScope(Job())
        val kept = Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { ioDispatcher ->
            val store = CameraFileStore(
                findDirectory = { slowToList },
                scope = discardScope,
                ioDispatcher = ioDispatcher,
                contentUriOf = ::contentUriOf
            )
            store.discardAll()
            val creating = async(start = CoroutineStart.UNDISPATCHED) { store.create() }
            listingAllowed.countDown()
            creating.await().also { discardScope.coroutineContext.job.children.toList().joinAll() }
        }

        assertTrue(File(kept.path).exists())
    }

    private fun TestScope.cameraFileStore(
        ioDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler)
    ): CameraFileStore = CameraFileStore(
        findDirectory = { directory },
        scope = backgroundScope,
        ioDispatcher = ioDispatcher,
        contentUriOf = ::contentUriOf
    )

    private fun contentUriOf(file: File): String = CONTENT_URI_PREFIX + file.name
}
