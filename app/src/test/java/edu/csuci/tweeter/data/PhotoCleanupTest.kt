@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.data

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DELETE_MILLIS = 1_000L

class PhotoCleanupTest {
    @Test
    fun cleanUpUnusedPhoto_deletes() = runTest {
        var isDeleted = false

        cleanUpUnusedPhoto { isDeleted = true }

        assertTrue(isDeleted)
    }

    @Test
    fun cleanUpUnusedPhoto_deletionFails_doesNotThrow() = runTest {
        cleanUpUnusedPhoto { throw PhotoDeletionException(IOException("Offline")) }
    }

    @Test
    fun cleanUpUnusedPhoto_callerCancelled_deletesAnyway() = runTest {
        var isDeleted = false
        val cleanup = launch {
            cleanUpUnusedPhoto {
                delay(DELETE_MILLIS)
                isDeleted = true
            }
        }
        testScheduler.runCurrent()

        cleanup.cancel()
        testScheduler.advanceUntilIdle()

        assertTrue(isDeleted)
    }

    @Test
    fun cleanUpUnusedPhoto_deletionHangs_givesUpAfterTimeout() = runTest {
        cleanUpUnusedPhoto { awaitCancellation() }

        assertEquals(PHOTO_CLEANUP_TIMEOUT_MILLIS, testScheduler.currentTime)
    }
}
