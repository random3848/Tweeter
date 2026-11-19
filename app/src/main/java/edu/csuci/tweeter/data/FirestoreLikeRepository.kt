package edu.csuci.tweeter.data

import com.google.firebase.FirebaseException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

// The subcollection of a user's profile document that holds one empty document per post they
// liked, named by the post's ID. The rules in firestore.rules match this name.
private const val LIKES = "likes"

/**
 * A [LikeRepository] that stores likes in Cloud Firestore, as the documents
 * `users/{uid}/likes/{postId}`. A like and the post's [Post.likeCount] change together, in one
 * batch, and the Firestore rules refuse a count that changes without its like, so the two always
 * agree.
 *
 * The liked posts come from one listener on the logged-in user's likes, set up again whenever
 * someone else logs in, and again with [retryWithBackoff] when it fails. Firestore shows a change
 * on this phone as soon as it is made, before the server has saved it, and takes it back if the
 * server refuses it.
 *
 * @param accountRepository Who is logged in, whose likes these are.
 * @param guestSignIn Signs a guest in if nobody is signed in, which [signedInSnapshots] does
 *     before setting up a listener. The likes listener runs only while a user is logged in, so
 *     it never signs a guest in.
 * @param dispatcher Where the listener delivers snapshots and they are read, off the main thread.
 */
class FirestoreLikeRepository(
    private val accountRepository: AccountRepository,
    private val guestSignIn: GuestSignIn,
    private val firestore: FirebaseFirestore,
    private val dispatcher: CoroutineDispatcher
) : LikeRepository {
    private val posts = firestore.collection(PostDocument.COLLECTION)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getLikedPostIds(): Flow<Set<String>> =
        accountRepository.loggedInUid.flatMapLatest { uid ->
            if (uid == null) flowOf(emptySet()) else likedPostIdsOf(uid)
        }

    override suspend fun like(postId: String) {
        changeLike(postId) { like, post ->
            set(like, emptyMap<String, Any>())
            update(post, PostDocument.Field.LIKE_COUNT.key, FieldValue.increment(1))
        }
    }

    override suspend fun unlike(postId: String) {
        changeLike(postId) { like, post ->
            delete(like)
            update(post, PostDocument.Field.LIKE_COUNT.key, FieldValue.increment(-1))
        }
    }

    private fun likedPostIdsOf(uid: String): Flow<Set<String>> =
        likesOf(uid).documentIds(guestSignIn, dispatcher, TAG)

    // Saves what [write] does to the logged-in user's like of the post with [postId] and to the
    // post itself, both or neither.
    private suspend fun changeLike(
        postId: String,
        write: WriteBatch.(like: DocumentReference, post: DocumentReference) -> Unit
    ) {
        val uid = accountRepository.loggedInUid.value ?:
            throw LikeException(IllegalStateException("Nobody is logged in"))
        val like = likesOf(uid).document(postId)
        val post = posts.document(postId)
        commit { write(like, post) }
    }

    private suspend fun commit(writes: WriteBatch.() -> Unit) {
        try {
            firestore.batch().apply(writes).commit().await()
        } catch (error: FirebaseException) {
            throw LikeException(error)
        }
    }

    private fun likesOf(uid: String): CollectionReference =
        firestore.collection(ProfileDocument.COLLECTION).document(uid).collection(LIKES)

    private companion object {
        const val TAG = "FirestoreLikeRepo"
    }
}
