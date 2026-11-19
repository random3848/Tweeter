@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.feed

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val POST_ID = "post"
private const val OTHER_POST_ID = "other post"

class PendingPostChangesTest {
    private val pendingChanges = PendingPostChanges()
    private val network = CompletableDeferred<Unit>()
    private var saveCount = 0

    @Test
    fun saveUnlessPending_runsSave() = runTest {
        pendingChanges.saveUnlessPending(POST_ID) { saveCount++ }

        assertEquals(1, saveCount)
    }

    @Test
    fun saveUnlessPending_samePostWhileSaving_skipsSave() = runTest {
        startSlowSave(POST_ID)

        startSlowSave(POST_ID)

        assertEquals(1, saveCount)
    }

    @Test
    fun saveUnlessPending_otherPostWhileSaving_runsBoth() = runTest {
        startSlowSave(POST_ID)

        startSlowSave(OTHER_POST_ID)

        assertEquals(2, saveCount)
    }

    @Test
    fun saveUnlessPending_afterSaveFinished_runsAgain() = runTest {
        startSlowSave(POST_ID)
        network.complete(Unit)

        startSlowSave(POST_ID)

        assertEquals(2, saveCount)
    }

    @Test
    fun saveUnlessPending_afterSaveFailed_runsAgain() = runTest {
        runCatching { pendingChanges.saveUnlessPending(POST_ID) { error("Refused") } }

        pendingChanges.saveUnlessPending(POST_ID) { saveCount++ }

        assertEquals(1, saveCount)
    }

    // Starts saving a change of the post, which waits for the network until the test completes it.
    private fun TestScope.startSlowSave(postId: String) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            pendingChanges.saveUnlessPending(postId) {
                saveCount++
                network.await()
            }
        }
    }
}
