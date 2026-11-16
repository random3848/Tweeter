@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.data

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ONCE = 1
private const val TWICE = 2

class GuestSignInTest {
    private var isSignedIn = false
    private var signInCount = 0

    // Completes the sign-in running, like Firebase answering; null for one that answers at once.
    private var signInFinished: CompletableDeferred<Unit>? = null

    private var signInFails = false

    @Test
    fun signInIfSignedOut_signedOut_signsIn() = runTest {
        guestSignIn().signInIfSignedOut()

        assertTrue(isSignedIn)
        assertEquals(ONCE, signInCount)
    }

    @Test
    fun signInIfSignedOut_signedIn_doesNotSignInAgain() = runTest {
        isSignedIn = true

        guestSignIn().signInIfSignedOut()

        assertEquals(0, signInCount)
    }

    @Test
    fun signInIfSignedOut_whileSigningIn_waitsForThatSignIn() = runTest {
        val finished = CompletableDeferred<Unit>().also { signInFinished = it }
        val guestSignIn = guestSignIn()
        val first = startSignIn(guestSignIn)
        val second = startSignIn(guestSignIn)

        finished.complete(Unit)

        assertTrue(first.isCompleted && second.isCompleted)
        assertEquals(ONCE, signInCount)
    }

    @Test
    fun signInIfSignedOut_firstCallerCancelled_signInGoesOnForOthers() = runTest {
        val finished = CompletableDeferred<Unit>().also { signInFinished = it }
        val guestSignIn = guestSignIn()
        val first = startSignIn(guestSignIn)
        val second = startSignIn(guestSignIn)
        first.cancel()

        finished.complete(Unit)

        assertTrue(second.isCompleted && isSignedIn)
    }

    @Test
    fun signInIfSignedOut_afterFailedSignIn_triesAgain() = runTest {
        signInFails = true
        val guestSignIn = guestSignIn()
        runCatching { guestSignIn.signInIfSignedOut() }
        signInFails = false

        guestSignIn.signInIfSignedOut()

        assertEquals(TWICE, signInCount)
        assertTrue(isSignedIn)
    }

    // Signs in in a scope of its own, as the app's, which a failed sign-in doesn't cancel.
    private fun TestScope.guestSignIn(): GuestSignIn = GuestSignIn(
        isSignedIn = { isSignedIn },
        signInAnonymously = ::signInAnonymously,
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
    )

    // Signs in from a caller of its own, which runs until it waits.
    private fun TestScope.startSignIn(guestSignIn: GuestSignIn): Job =
        launch(UnconfinedTestDispatcher(testScheduler)) { guestSignIn.signInIfSignedOut() }

    private suspend fun signInAnonymously() {
        signInCount++
        signInFinished?.await()
        if (signInFails) {
            throw IOException("No connection")
        }
        isSignedIn = true
    }
}
