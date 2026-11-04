package edu.csuci.tweeter.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
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
 * [ProfileDocument]'s fields, into one array per listener, instead of copying each document into
 * a map.
 */
class FirestoreProfileRepository(firestore: FirebaseFirestore) : ProfileRepository {
    private val profiles = firestore.collection(ProfileDocument.COLLECTION)

    override fun getProfile(uid: String): Flow<UserProfile> = callbackFlow {
        val fields = arrayOfNulls<Any>(fieldPaths.size)
        val registration = profiles.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                trySend(ProfileDocument.toProfile(uid, snapshot.readInto(fields)))
            }
        }

        awaitClose { registration.remove() }
    }

    // The profile's fields read into [fields], or null when the user has no document.
    private fun DocumentSnapshot.readInto(fields: Array<Any?>): Array<Any?>? {
        if (!exists()) {
            return null
        }
        for (index in fieldPaths.indices) {
            fields[index] = get(fieldPaths[index])
        }
        return fields
    }

    override suspend fun saveProfile(profile: UserProfile) {
        profiles.document(profile.uid).set(ProfileDocument.fieldsOf(profile)).await()
    }

    private companion object {
        val fieldPaths = Array(ProfileDocument.Field.entries.size) {
            FieldPath.of(ProfileDocument.Field.entries[it].key)
        }
    }
}
