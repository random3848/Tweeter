package edu.csuci.tweeter.data

import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
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
                    trySend(snapshot.exists())
                }
            }

            awaitClose { registration.remove() }
        }

    override fun observeFollowerCount(userUid: String): Flow<Int> = callbackFlow {
        val followers = firestore
            .collection(ProfileDocument.COLLECTION)
            .document(userUid)
            .collection(FOLLOWERS)

        val registration = followers.addSnapshotListener(
            MetadataChanges.INCLUDE
        ) { snapshot, error ->
            if (error != null) {
                close(FollowException(error))
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.metadata.isFromCache) {
                trySend(snapshot.size())
            }
        }

        awaitClose { registration.remove() }
    }

    override fun observeFollowingCount(userUid: String): Flow<Int> = callbackFlow {
        val registration = firestore
            .collectionGroup(FOLLOWERS)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(FollowException(error))
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.metadata.isFromCache) {
                    val count = snapshot.documents.count { document ->
                        document.id == userUid
                    }
                    trySend(count)
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
        relationship(followerUid, userUid)
            .set(emptyMap<String, Any>())
            .await()
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
