package edu.csuci.tweeter.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * A [PostRepository] that stores posts in Cloud Firestore, so they last and every user sees them.
 *
 * Posts are stored as described by [PostDocument]. Creates an anonymous session when no user is
 * signed in, since the Firestore rules only let signed-in users read and write.
 *
 * [getPostTable] returns the same flow to every caller: one snapshot listener, started in
 * [scope] while anyone collects and removed as soon as nobody does, so every screen shares one
 * listener and one parse per snapshot. Snapshots are delivered and parsed on [dispatcher], off
 * the main thread, one at a time in the order they arrive, so an older snapshot can never replace
 * a newer one. Only [PostDocument]'s fields are read, into one array reused for every document.
 */
class FirestorePostRepository(
    private val auth: FirebaseAuth,
    firestore: FirebaseFirestore,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher
) : PostRepository {
    private val posts = firestore.collection(PostDocument.COLLECTION)
    private val newestFirst =
        posts.orderBy(PostDocument.Field.TIMESTAMP.key, Query.Direction.DESCENDING)
    private val postTable = snapshotsOf(newestFirst, ::tableOf).sharedWhileCollected(scope)

    override fun getPostTable(): Flow<PostTable> = postTable

    override suspend fun addPost(post: Post) {
        signIn()
        posts.document(post.id).set(PostDocument.fieldsOf(post)).await()
    }

    // [read] of each snapshot of [query], made on [dispatcher] one snapshot at a time.
    private fun <T> snapshotsOf(query: Query, read: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
        signIn()

        val executor = dispatcher.serialExecutor()
        val registration = query.addSnapshotListener(executor) { snapshot, error ->
            if (error != null) {
                // Keep showing the last posts; the listener stops after an error.
                Log.e(TAG, "Could not read posts", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(read(snapshot))
            }
        }

        awaitClose { registration.remove() }
    }

    // The well-formed posts of [snapshot], in order; the others are logged and left out.
    private fun tableOf(snapshot: QuerySnapshot): PostTable {
        val builder = PostTable.Builder(capacity = snapshot.size())
        val fields = arrayOfNulls<Any>(fieldPaths.size)
        for (document in snapshot.documents) {
            fields.fillFrom(document)
            val post = PostDocument.toPost(document.id, fields)
            if (post == null) {
                Log.w(TAG, "Skipping malformed post ${document.id}")
            } else {
                builder.add(post)
            }
        }
        return builder.build()
    }

    private suspend fun signIn() {
        // Guests browse with an anonymous session; the Firestore rules don't let it post.
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    private companion object {
        const val TAG = "FirestorePostRepo"

        val fieldPaths = fieldPathsOf(PostDocument.Field.entries)

        // Overwrites this array with [document]'s fields, in PostDocument.Field order. A post this
        // phone just added has no server time yet; until Firestore sets it, use Firestore's
        // estimate so the post shows up right away.
        fun Array<Any?>.fillFrom(document: DocumentSnapshot) {
            for (index in fieldPaths.indices) {
                this[index] = document.get(fieldPaths[index], ServerTimestampBehavior.ESTIMATE)
            }
        }
    }
}
