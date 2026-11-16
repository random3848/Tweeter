package edu.csuci.tweeter.data

import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val FOLLOWERS = "followers"

/**
 * Follower relationships stored under each user's Firestore profile.
 *
 * A document at users/{followedUid}/followers/{followerUid} represents a follow.
 * The document has no fields.
 *
 * @param auth Identifies the registered user changing a relationship.
 * @param firestore Stores and observes follower relationships.
 */
class FirestoreFollowRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : FollowRepository {
    override fun observeFollowing(followerUid: String, followedUid: String): Flow<Boolean> =
        callbackFlow {
            val reference = relationship(followerUid, followedUid)
            val registration = reference.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(FollowException(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    Log.d("FollowDebug", "Following observed: ${snapshot.exists()}")
                    trySend(snapshot.exists())
                }
            }

            awaitClose { registration.remove() }
        }

    override suspend fun follow(userUid: String) {
        try {
            createRelationship(userUid)
        } catch (error: FirebaseException) {
            throw FollowException(error)
        }
    }

    private suspend fun createRelationship(userUid: String) {
        val followerUid = registeredFollowerUid(userUid)
        val reference = relationship(followerUid, userUid)

        firestore.runTransaction { transaction ->
            if (!transaction.get(reference).exists()) {
                transaction.set(reference, emptyMap<String, Any>())
            }
            Unit
        }.await()
    }

    override suspend fun unfollow(userUid: String) {
        try {
            deleteRelationship(userUid)
        } catch (error: FirebaseException) {
            throw FollowException(error)
        }
    }

    private suspend fun deleteRelationship(userUid: String) {
        val followerUid = registeredFollowerUid(userUid)
        relationship(followerUid, userUid).delete().await()
    }

    private fun registeredFollowerUid(userUid: String): String {
        val user = auth.currentUser
        if (user == null || user.isAnonymous) {
            throw FollowException(
                IllegalStateException("Log in before changing follower relationships")
            )
        }

        if (user.uid == userUid) {
            throw FollowException(
                IllegalArgumentException("Users cannot follow themselves")
            )
        }

        return user.uid
    }

    private fun relationship(followerUid: String, followedUid: String): DocumentReference =
        firestore
            .collection(ProfileDocument.COLLECTION)
            .document(followedUid)
            .collection(FOLLOWERS)
            .document(followerUid)
}
