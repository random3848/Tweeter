package edu.csuci.tweeter.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Signs in to Firebase anonymously whenever nobody is signed in, so that guests can browse, one
 * sign-in at a time: a caller that comes while one runs waits for it, instead of creating a second
 * anonymous account that nothing would use.
 *
 * The sign-in runs in [scope], so a caller that stops waiting doesn't stop it for the others.
 *
 * @param isSignedIn Whether anyone, a guest or not, is signed in.
 * @param signInAnonymously Signs in a new anonymous user.
 */
class GuestSignIn(
    private val isSignedIn: () -> Boolean,
    private val signInAnonymously: suspend () -> Unit,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()

    // The latest sign-in, which may still run. Guarded by [mutex].
    private var latestSignIn: Deferred<Unit>? = null

    /**
     * Signs in anonymously unless someone is signed in, or waits for the sign-in already running.
     * Throws what signing in throws, to every caller waiting for it.
     */
    suspend fun signInIfSignedOut() {
        val signIn = mutex.withLock { runningOrNewSignIn() } ?: return
        signIn.await()
    }

    // The sign-in still running, or a new one if nobody is signed in, or null if someone is.
    private fun runningOrNewSignIn(): Deferred<Unit>? {
        val running = latestSignIn?.takeIf { it.isActive }
        if (running != null) {
            return running
        }
        if (isSignedIn()) {
            return null
        }
        return scope.async { signInAnonymously() }.also { latestSignIn = it }
    }

    /** Creates [GuestSignIn]s. */
    companion object {
        /** Signs guests in to [auth], running each sign-in in [scope]. */
        fun to(auth: FirebaseAuth, scope: CoroutineScope): GuestSignIn = GuestSignIn(
            isSignedIn = { auth.currentUser != null },
            signInAnonymously = { auth.signInAnonymously().await() },
            scope = scope
        )
    }
}
