package edu.csuci.tweeter.data

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

// The subcollection of a user's profile document that holds one empty document per post they
// saved, named by the post's ID. The rules in firestore.rules match this name.
private const val BOOKMARKS = "bookmarks"

/**
 * A [BookmarkRepository] that stores bookmarks in Cloud Firestore, as the empty documents
 * `users/{uid}/bookmarks/{postId}`, which the Firestore rules let only that user read, add and
 * delete.
 *
 * The saved posts come from one listener on the logged-in user's bookmarks, set up again whenever
 * someone else logs in, and again with [retryWithBackoff] when it fails. Firestore shows a change
 * on this phone as soon as it is made, before the server has saved it, and takes it back if the
 * server refuses it.
 *
 * @param accountRepository Who is logged in, whose bookmarks these are.
 * @param guestSignIn Signs a guest in if nobody is signed in, which [signedInSnapshots] does
 *     before setting up a listener. The bookmarks listener runs only while a user is logged in, so
 *     it never signs a guest in.
 * @param dispatcher Where the listener delivers snapshots and they are read, off the main thread.
 */
class FirestoreBookmarkRepository(
    private val accountRepository: AccountRepository,
    private val guestSignIn: GuestSignIn,
    private val firestore: FirebaseFirestore,
    private val dispatcher: CoroutineDispatcher
) : BookmarkRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getBookmarkedPostIds(): Flow<Set<String>> =
        accountRepository.loggedInUid.flatMapLatest { uid ->
            if (uid == null) flowOf(emptySet()) else bookmarkedPostIdsOf(uid)
        }

    override suspend fun bookmark(postId: String) {
        changeBookmark(postId) { bookmark -> bookmark.set(emptyMap<String, Any>()) }
    }

    override suspend fun unbookmark(postId: String) {
        changeBookmark(postId, DocumentReference::delete)
    }

    private fun bookmarkedPostIdsOf(uid: String): Flow<Set<String>> =
        bookmarksOf(uid).documentIds(guestSignIn, dispatcher, TAG)

    // Saves what [write] does to the logged-in user's bookmark of the post with [postId].
    private suspend fun changeBookmark(
        postId: String,
        write: (bookmark: DocumentReference) -> Task<Void>
    ) {
        val uid = accountRepository.loggedInUid.value ?:
            throw BookmarkException(IllegalStateException("Nobody is logged in"))
        val bookmark = bookmarksOf(uid).document(postId)
        save { write(bookmark) }
    }

    private suspend fun save(write: () -> Task<Void>) {
        try {
            write().await()
        } catch (error: FirebaseException) {
            throw BookmarkException(error)
        }
    }

    private fun bookmarksOf(uid: String): CollectionReference =
        firestore.collection(ProfileDocument.COLLECTION).document(uid).collection(BOOKMARKS)

    private companion object {
        const val TAG = "FirestoreBookmarkRepo"
    }
}
