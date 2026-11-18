package edu.csuci.tweeter.data

import com.google.firebase.FirebaseException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val COMMENTS = "comments"
private const val AUTHOR_UID = "authorUid"
private const val USERNAME = "username"
private const val TEXT = "text"
private const val TIMESTAMP = "timestamp"

/**
 * Comments stored in Firestore at posts/{postId}/comments/{commentId}.
 *
 * @param firestore Stores the comments.
 */
class FirestoreCommentRepository(private val firestore: FirebaseFirestore) : CommentRepository {
    override fun getComments(postId: String): Flow<List<Comment>> = callbackFlow {
        val registration = commentsOf(postId)
            .orderBy(TIMESTAMP, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(CommentException(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull(::commentOf))
                }
            }

        awaitClose { registration.remove() }
    }

    override suspend fun addComment(postId: String, author: UserProfile, text: String) {
        val fields = mapOf(
            AUTHOR_UID to author.uid,
            USERNAME to author.displayName,
            TEXT to text,
            TIMESTAMP to FieldValue.serverTimestamp()
        )
        try {
            commentsOf(postId).add(fields).await()
        } catch (error: FirebaseException) {
            throw CommentException(error)
        }
    }

    private fun commentsOf(postId: String): CollectionReference =
        firestore.collection(PostDocument.COLLECTION).document(postId).collection(COMMENTS)

    // A comment this phone just sent has no server time yet; ESTIMATE shows it right away.
    private fun commentOf(document: DocumentSnapshot): Comment? {
        val text = document.getString(TEXT) ?: return null
        val timestamp = document.getTimestamp(TIMESTAMP, ServerTimestampBehavior.ESTIMATE) ?:
            return null
        return Comment(
            id = document.id,
            authorUid = document.getString(AUTHOR_UID) ?: "",
            username = document.getString(USERNAME) ?: "",
            text = text,
            timestamp = timestamp.toInstant()
        )
    }
}
