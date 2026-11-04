@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.location

import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val CONTENT = "photo"

class NewFileTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // The one thread of [singleThreadDispatcher], to tell whether a file was deleted on the
    // dispatcher it was given.
    private var ioThread: Thread? = null
    private val singleThreadDispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(task).also { ioThread = it }
    }.asCoroutineDispatcher()

    @After
    fun stopIoThread() {
        singleThreadDispatcher.close()
    }

    @Test
    fun writeNewFile_returnsWrittenFile() = runTest {
        val file = writeNewFile(
            dispatcher = ioDispatcher(),
            create = ::createFile,
            write = { it.writeText(CONTENT) }
        )

        assertEquals(CONTENT, file.readText())
    }

    @Test
    fun writeNewFile_writeFails_deletesFileAndRethrows() = runTest {
        val error = runCatching {
            writeNewFile(
                dispatcher = ioDispatcher(),
                create = ::createFile,
                write = { throw IOException("Disk full") }
            )
        }.exceptionOrNull()

        assertEquals(IOException::class, error?.let { it::class })
        assertEquals(emptyList<File>(), filesLeft())
    }

    @Test
    fun writeNewFile_cancelledWhileWriting_deletesFile() = runTest {
        launch {
            val caller = currentCoroutineContext().job
            writeNewFile(
                dispatcher = ioDispatcher(),
                create = ::createFile,
                write = { file ->
                    file.writeText(CONTENT)
                    caller.cancel()
                }
            )
        }

        testScheduler.advanceUntilIdle()

        assertEquals(emptyList<File>(), filesLeft())
    }

    @Test
    fun writeNewFile_writeFails_deletesFileOnDispatcher() = runBlocking {
        val file = ThreadRecordingFile(createFile())

        runCatching {
            writeNewFile(
                dispatcher = singleThreadDispatcher,
                create = { file },
                write = { throw IOException("Disk full") }
            )
        }

        assertSame(ioThread, file.deletingThread)
    }

    @Test
    fun deleteFile_deletesOnDispatcher() = runBlocking {
        val file = ThreadRecordingFile(createFile())

        deleteFile(singleThreadDispatcher, file)

        assertFalse(file.exists())
        assertSame(ioThread, file.deletingThread)
    }

    @Test
    fun deleteFile_callerCancelled_deletesAnyway() = runTest {
        val file = createFile()
        launch {
            currentCoroutineContext().job.cancel()
            deleteFile(ioDispatcher(), file)
        }

        testScheduler.advanceUntilIdle()

        assertFalse(file.exists())
    }

    private fun TestScope.ioDispatcher() = StandardTestDispatcher(testScheduler)

    private fun createFile(): File = temporaryFolder.newFile()

    private fun filesLeft(): List<File> = temporaryFolder.root.listFiles().orEmpty().toList()

    // A file that remembers which thread deleted it.
    private class ThreadRecordingFile(file: File) : File(file.path) {
        var deletingThread: Thread? = null
            private set

        override fun delete(): Boolean {
            deletingThread = Thread.currentThread()
            return super.delete()
        }
    }
}
