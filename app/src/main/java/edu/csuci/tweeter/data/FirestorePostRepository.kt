package edu.csuci.tweeter.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    override fun getPosts(): Flow<List<Post>> = callbackFlow {
        signIn()

        val registration = posts
            .orderBy(PostDocument.TIMESTAMP, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Keep showing the last posts; the listener stops after an error.
                    Log.e("FirestorePostRepo", "Could not read posts", error)
                    return@addSnapshotListener
                }

                val documents = snapshot?.documents ?: return@addSnapshotListener
                val postList = documents.mapNotNull { document ->
                    val post = PostDocument.toPost(document.id, document.data.orEmpty())
                    if (post == null) {
                        Log.w("FirestorePostRepo", "Skipping malformed post ${document.id}")
                    }
                    post
                }
                trySend(postList)
            }

        awaitClose { registration.remove() }
    }

    override suspend fun addPost(post: Post) {
        signIn()
        posts.document(post.id).set(PostDocument.fieldsOf(post)).await()
    }

    private suspend fun signIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }
}
