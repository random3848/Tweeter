package edu.csuci.tweeter.data

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Each snapshot of this query, from a listener that delivers them on [dispatcher] one at a time,
 * in the order they arrive. The newest snapshot replaces any that wasn't collected yet, so it
 * can't be dropped, and stale ones aren't read. The listener stops after an error, so the flow
 * then fails with it; collecting it again sets up a new listener.
 */
internal fun Query.snapshotsOn(dispatcher: CoroutineDispatcher): Flow<QuerySnapshot> =
    callbackFlow {
        val registration = addSnapshotListener(dispatcher.serialExecutor()) { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(snapshot)
            }
        }

        awaitClose { registration.remove() }
    }.conflate()

/**
 * Whether this snapshot came from the server. One from the phone's cache can come first even
 * when the server then rejects the listener, so it doesn't show the listener works again.
 */
internal val QuerySnapshot.isFromServer: Boolean
    get() = !metadata.isFromCache
