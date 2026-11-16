package edu.csuci.tweeter.data

import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val FOLLOWERS = "followers"
private const val FOLLOWING = "following"

/**
 * Follower relationships stored under both users' Firestore profiles.
 *
 * A follow is two documents, written and deleted together: users/{followedUid}/followers/
 * {followerUid}, which lists a user's followers, and users/{followerUid}/following/{followedUid},
 * which lists whom a user follows. Neither has fields.
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
            val reference = followerDocument(followerUid, followedUid)
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

    override fun observeFollowedUids(followerUid: String): Flow<Set<String>> =
        observeDocumentIds(userCollection(followerUid, FOLLOWING))

    override fun observeFollowerUids(followedUid: String): Flow<Set<String>> =
        observeDocumentIds(userCollection(followedUid, FOLLOWERS))

    // The IDs of the documents in [collection], which name the users it lists.
    private fun observeDocumentIds(collection: CollectionReference): Flow<Set<String>> =
        callbackFlow {
            val registration = collection.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(FollowException(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    trySend(snapshot.documents.mapTo(HashSet(), DocumentSnapshot::getId))
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
        firestore.batch()
            .set(followerDocument(followerUid, userUid), emptyMap<String, Any>())
            .set(followingDocument(followerUid, userUid), emptyMap<String, Any>())
            .commit()
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
        firestore.batch()
            .delete(followerDocument(followerUid, userUid))
            .delete(followingDocument(followerUid, userUid))
            .commit()
            .await()
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

    // The relationship in the followed user's list of followers.
    private fun followerDocument(followerUid: String, followedUid: String): DocumentReference =
        userCollection(followedUid, FOLLOWERS).document(followerUid)

    // The relationship in the follower's list of users they follow.
    private fun followingDocument(followerUid: String, followedUid: String): DocumentReference =
        userCollection(followerUid, FOLLOWING).document(followedUid)

    private fun userCollection(uid: String, name: String): CollectionReference =
        firestore.collection(ProfileDocument.COLLECTION).document(uid).collection(name)
}
