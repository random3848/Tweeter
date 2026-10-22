package edu.csuci.tweeter.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A [ProfileRepository] that stores profiles in Cloud Firestore, as described by
 * [ProfileDocument].
 *
 * It reads profiles for users who are already signed in to Firebase, so unlike
 * [FirestorePostRepository] it doesn't sign in itself.
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
                trySend(ProfileDocument.toProfile(uid, snapshot.data))
            }
        }

        awaitClose { registration.remove() }
    }
}
