package edu.csuci.tweeter.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * A [ProfileRepository] that stores profiles in Cloud Firestore, as described by
 * [ProfileDocument].
 *
 * It reads profiles for users who are already signed in to Firebase and saves the logged-in
 * user's own, so unlike [FirestorePostRepository] it doesn't sign in itself. It reads only
 * [ProfileDocument]'s fields, into an array, instead of copying each document into a map.
 */
class FirestoreProfileRepository(firestore: FirebaseFirestore) : ProfileRepository {
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

    // The profile's fields in ProfileDocument.Field order, or null when the user has no document.
    private fun DocumentSnapshot.profileFields(): Array<Any?>? {
        if (!exists()) {
            return null
        }
        return Array(fieldPaths.size) { get(fieldPaths[it]) }
    }

    override suspend fun saveProfile(profile: UserProfile) {
        profiles.document(profile.uid).set(ProfileDocument.fieldsOf(profile)).await()
    }

    private companion object {
        val fieldPaths = fieldPathsOf(ProfileDocument.Field.entries)
    }
}
