package edu.csuci.tweeter.data

import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
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
                    trySend(followedUidsIn(snapshot, followerUid = userUid).size)
                }
            }

        awaitClose { registration.remove() }
    }

    // Each snapshot holds every follow, so only the newest of those not collected yet is kept.
    override fun observeFollowedUids(followerUid: String): Flow<Set<String>> = callbackFlow {
        val registration = firestore
            .collectionGroup(FOLLOWERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(FollowException(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    trySend(followedUidsIn(snapshot, followerUid))
                }
            }

        awaitClose { registration.remove() }
    }.conflate()

    // The users [followerUid] follows, among every follow in [follows]: each follow document is
    // named by its follower, under the followed user's profile.
    private fun followedUidsIn(follows: QuerySnapshot, followerUid: String): Set<String> =
        follows.documents
            .filter { document -> document.id == followerUid }
            .mapNotNullTo(HashSet()) { document -> document.reference.parent.parent?.id }

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
        followersOf(followedUid).document(followerUid)

    // The documents of the users who follow user [uid], one per follower, named by their ID.
    private fun followersOf(uid: String): CollectionReference =
        firestore.collection(ProfileDocument.COLLECTION).document(uid).collection(FOLLOWERS)
}
