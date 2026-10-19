package edu.csuci.tweeter.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.util.UUID
import kotlinx.coroutines.tasks.await

/**
 * Uploads camera JPEGs to the signed-in user's Firebase Storage folder.
 *
 * Creates an anonymous session when no user is signed in.
 */
class FirebasePhotoRepository(
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage
) : PhotoRepository {
    override suspend fun uploadPhoto(uri: Uri): String {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
        checkNotNull(user) { "Firebase sign-in returned no user" }

        val fileName = "${UUID.randomUUID()}.jpg"
        val photoReference = storage.reference
            .child("images/${user.uid}/$fileName")

        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        photoReference.putFile(uri, metadata).await()

        return photoReference.downloadUrl.await().toString()
    }
}
