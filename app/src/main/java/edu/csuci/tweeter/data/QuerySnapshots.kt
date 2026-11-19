package edu.csuci.tweeter.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Each snapshot of this query, from a listener set up once [guestSignIn] has signed a guest in if
 * nobody is signed in, since the Firestore rules only let signed-in users read. The listener
 * delivers snapshots on [dispatcher] one at a time, in the order they arrive. The newest snapshot
 * replaces any that wasn't collected yet, so it can't be dropped, and stale ones aren't read. The
 * listener stops after an error, so the flow then fails with it; collecting it again signs in if
 * needed and sets up a new listener.
 */
internal fun Query.signedInSnapshots(
    guestSignIn: GuestSignIn,
    dispatcher: CoroutineDispatcher
): Flow<QuerySnapshot> = signedInSnapshotsOf(guestSignIn, dispatcher) { executor, listener ->
    addSnapshotListener(executor, listener)
}

/** Each snapshot of this document, from a listener set up as for [Query.signedInSnapshots]. */
internal fun DocumentReference.signedInSnapshots(
    guestSignIn: GuestSignIn,
    dispatcher: CoroutineDispatcher
): Flow<DocumentSnapshot> = signedInSnapshotsOf(guestSignIn, dispatcher) { executor, listener ->
    addSnapshotListener(executor, listener)
}

/**
 * The IDs of this query's documents, as each of its [signedInSnapshots] holds them, read on
 * [dispatcher]. When the listener fails, it is set up again with [retryWithBackoff], which logs
 * the failure under [tag].
 */
internal fun Query.documentIds(
    guestSignIn: GuestSignIn,
    dispatcher: CoroutineDispatcher,
    tag: String
): Flow<Set<String>> = signedInSnapshots(guestSignIn, dispatcher)
    .retryWithBackoff(tag, resetsBackoff = QuerySnapshot::isFromServer)
    .map { snapshot -> snapshot.documents.mapTo(HashSet()) { it.id } }
    .flowOn(dispatcher)

/**
 * Whether this snapshot came from the server. One from the phone's cache can come first even
 * when the server then rejects the listener, so it doesn't show the listener works again.
 */
internal val QuerySnapshot.isFromServer: Boolean
    get() = !metadata.isFromCache

/** Whether this snapshot came from the server, as for [QuerySnapshot.isFromServer]. */
internal val DocumentSnapshot.isFromServer: Boolean
    get() = !metadata.isFromCache

// The snapshots of the listener that [addListener] sets up, as Query.signedInSnapshots describes.
private fun <T> signedInSnapshotsOf(
    guestSignIn: GuestSignIn,
    dispatcher: CoroutineDispatcher,
    addListener: (Executor, EventListener<T>) -> ListenerRegistration
): Flow<T> = callbackFlow {
    guestSignIn.signInIfSignedOut()

    val listener = EventListener<T> { snapshot, error ->
        if (error != null) {
            close(error)
            return@EventListener
        }
        if (snapshot != null) {
            trySend(snapshot)
        }
    }
    val registration = addListener(dispatcher.serialExecutor(), listener)

    awaitClose { registration.remove() }
}.conflate()
