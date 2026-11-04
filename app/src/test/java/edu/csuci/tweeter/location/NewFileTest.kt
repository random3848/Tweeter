@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.location

import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val CONTENT = "photo"

class NewFileTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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

    private fun TestScope.ioDispatcher() = StandardTestDispatcher(testScheduler)

    private fun createFile(): File = temporaryFolder.newFile()

    private fun filesLeft(): List<File> = temporaryFolder.root.listFiles().orEmpty().toList()
}
