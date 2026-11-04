package edu.csuci.tweeter.data

import android.net.Uri
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.tasks.await

/**
 * Uploads camera JPEGs to the logged-in user's Firebase Storage folder.
 *
 * Guests can only browse, so the Storage rules refuse uploads from anonymous sessions.
 */
class FirebasePhotoRepository(
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage
) : PhotoRepository {
    override suspend fun uploadPhoto(uri: Uri): String {
        try {
            val user = auth.currentUser
            if (user == null || user.isAnonymous) {
                throw PhotoUploadException(IllegalStateException("Nobody is logged in"))
            }

            val fileName = "${UUID.randomUUID()}.jpg"
            val photoReference = storage.reference
                .child("images/${user.uid}/$fileName")

            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()

            photoReference.putFile(uri, metadata).await()

            return photoReference.downloadUrl.await().toString()
        } catch (error: FirebaseException) {
            throw PhotoUploadException(error)
        } catch (error: IOException) {
            throw PhotoUploadException(error)
        } catch (error: SecurityException) {
            throw PhotoUploadException(error)
        }
    }
}
