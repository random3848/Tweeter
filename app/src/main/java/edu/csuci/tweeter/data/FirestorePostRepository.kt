package edu.csuci.tweeter.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * A [PostRepository] that stores posts in Cloud Firestore, so they last and every user sees them.
 *
 * Posts are stored as described by [PostDocument]. Creates an anonymous session when no user is
 * signed in, since the Firestore rules only let signed-in users read and write.
 */
class FirestorePostRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : PostRepository {
    private val posts = firestore.collection(PostDocument.COLLECTION)

    override fun getPosts(): Flow<List<Post>> =
        postsIn(posts.orderBy(PostDocument.TIMESTAMP, Query.Direction.DESCENDING))

    // Sorted here rather than with orderBy, which together with whereEqualTo would need a
    // composite index in Firestore.
    override fun getPostsBy(authorUid: String): Flow<List<Post>> =
        postsIn(posts.whereEqualTo(PostDocument.AUTHOR_UID, authorUid))
            .map { postList -> postList.sortedByDescending { it.timestamp } }

    override suspend fun addPost(post: Post) {
        signIn()
        posts.document(post.id).set(PostDocument.fieldsOf(post)).await()
    }

    // The posts [query] matches, emitted again whenever they change.
    private fun postsIn(query: Query): Flow<List<Post>> = callbackFlow {
        signIn()

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Keep showing the last posts; the listener stops after an error.
                Log.e("FirestorePostRepo", "Could not read posts", error)
                return@addSnapshotListener
            }

            val documents = snapshot?.documents ?: return@addSnapshotListener
            val postList = documents.mapNotNull { document ->
                // A post this phone just added has no server time yet; until Firestore
                // sets it, use Firestore's estimate so the post shows up right away.
                val fields = document.getData(ServerTimestampBehavior.ESTIMATE).orEmpty()
                val post = PostDocument.toPost(document.id, fields)
                if (post == null) {
                    Log.w("FirestorePostRepo", "Skipping malformed post ${document.id}")
                }
                post
            }
            trySend(postList)
        }

        awaitClose { registration.remove() }
    }

    private suspend fun signIn() {
        // Anonymous until create-account/login is built; that work changes or removes this.
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }
}
