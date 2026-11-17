package edu.csuci.tweeter.data

import com.google.firebase.FirebaseException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * A [ProfileRepository] that stores profiles in Cloud Firestore, as described by
 * [ProfileDocument].
 *
 * It reads profiles for users who are already signed in to Firebase and saves the logged-in
 * user's own, so unlike [FirestorePostRepository] it doesn't sign in itself. It reads only
 * [ProfileDocument]'s fields, into an array, instead of copying each document into a map.
 *
 * [getProfiles] parses its snapshots on [dispatcher], off the main thread, one at a time in the
 * order they arrive, keeping only the newest of those that arrive during a parse. When its
 * listener fails, as it does before a guest is signed in, it's set up again with
 * [retryWithBackoff], while collectors keep the last profiles.
 *
 * @param firestore Where the profiles are stored.
 * @param dispatcher Where [getProfiles] parses every profile.
 */
class FirestoreProfileRepository(
    firestore: FirebaseFirestore,
    private val dispatcher: CoroutineDispatcher
) : ProfileRepository {
    private val profiles = firestore.collection(ProfileDocument.COLLECTION)

    override fun getProfile(uid: String): Flow<UserProfile> = callbackFlow {
        val registration = profiles.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                trySend(ProfileDocument.toProfile(uid, snapshot.profileFields()))
            }
        }

        awaitClose { registration.remove() }
    }

    override fun getProfiles(): Flow<List<UserProfile>> = profiles.snapshotsOn(dispatcher)
        .retryWithBackoff(TAG, resetsBackoff = QuerySnapshot::isFromServer)
        .map(::profilesOf)
        .flowOn(dispatcher)

    private fun profilesOf(snapshot: QuerySnapshot): List<UserProfile> =
        snapshot.documents.map { document ->
            ProfileDocument.toProfile(document.id, document.profileFields())
        }

    // The profile's fields in ProfileDocument.Field order, or null when the user has no document.
    private fun DocumentSnapshot.profileFields(): Array<Any?>? {
        if (!exists()) {
            return null
        }
        return Array(fieldPaths.size) { get(fieldPaths[it]) }
    }

    override suspend fun saveProfile(profile: UserProfile) {
        try {
            profiles.document(profile.uid).set(ProfileDocument.fieldsOf(profile)).await()
        } catch (error: FirebaseException) {
            throw ProfileSaveException(error)
        }
    }

    private companion object {
        const val TAG = "FirestoreProfileRepo"

        val fieldPaths = fieldPathsOf(ProfileDocument.Field.entries)
    }
}
