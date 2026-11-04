package edu.csuci.tweeter.data

import android.net.Uri
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import edu.csuci.tweeter.location.LocationTagRemover
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.tasks.await

// The Storage folders photos go in, each with a subfolder per user. The rules in storage.rules
// match these names.
private const val POST_PHOTOS = "images"
private const val PROFILE_PHOTOS = "profile_photos"

/**
 * Uploads JPEGs to the logged-in user's folders in Firebase Storage.
 *
 * Guests can only browse, so the Storage rules refuse uploads from anonymous sessions.
 *
 * @param locationTagRemover Removes location tags from profile photos before they're uploaded.
 */
class FirebasePhotoRepository(
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val locationTagRemover: LocationTagRemover
) : PhotoRepository {
    override suspend fun uploadPhoto(uri: Uri): String = upload(uri, POST_PHOTOS)

    override suspend fun uploadProfilePhoto(photoUri: String): String {
        val copy = copyWithoutLocation(photoUri)
        try {
            return upload(Uri.fromFile(copy), PROFILE_PHOTOS)
        } finally {
            copy.delete()
        }
    }

    private suspend fun copyWithoutLocation(photoUri: String): File = try {
        locationTagRemover.copyWithoutLocation(Uri.parse(photoUri))
    } catch (error: IOException) {
        throw PhotoUploadException(error)
    }

    // Uploads [uri] as a JPEG into the logged-in user's subfolder of [folder].
    private suspend fun upload(uri: Uri, folder: String): String {
        try {
            val user = auth.currentUser
            if (user == null || user.isAnonymous) {
                throw PhotoUploadException(IllegalStateException("Nobody is logged in"))
            }

            val fileName = "${UUID.randomUUID()}.jpg"
            val photoReference = storage.reference
                .child("$folder/${user.uid}/$fileName")

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
