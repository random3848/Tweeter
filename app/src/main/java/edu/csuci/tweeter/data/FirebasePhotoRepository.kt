package edu.csuci.tweeter.data

import android.net.Uri
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.tasks.await

/**
 * Uploads camera JPEGs to the signed-in user's Firebase Storage folder and fetches uploaded
 * photos.
 *
 * Creates an anonymous session when no user is signed in.
 */
class FirebasePhotoRepository(
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage
) : PhotoRepository {
    override suspend fun uploadPhoto(uri: Uri): String {
        try {
            val user = auth.currentUser ?: auth.signInAnonymously().await().user
            if (user == null) {
                throw PhotoUploadException(
                    IllegalStateException("Firebase sign-in returned no user")
                )
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

    override suspend fun fetchPhotoUrls(): List<String> = try {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
        checkNotNull(user) { "Firebase sign-in returned no user" }

        val photosWithTime = mutableListOf<Pair<String, Long>>()

        try {
            val userFolder = storage.reference.child("images/${user.uid}")
            val userFiles = userFolder.listAll().await().items
            for (fileRef in userFiles) {
                try {
                    val url = fileRef.downloadUrl.await().toString()
                    val metadata = fileRef.metadata.await()
                    val time = metadata.updatedTimeMillis.takeIf { it > 0 } ?:
                        metadata.creationTimeMillis
                    photosWithTime.add(Pair(url, time))
                } catch (error: Exception) {
                    Log.e("FirebasePhotoRepo", "Could not get photo metadata", error)
                }
            }
        } catch (error: Exception) {
            Log.e("FirebasePhotoRepo", "Could not list user folder images/${user.uid}", error)
        }

        try {
            val imagesRef = storage.reference.child("images")
            val userFolders = imagesRef.listAll().await().prefixes
            for (folderRef in userFolders) {
                if (folderRef.name != user.uid) {
                    val files = folderRef.listAll().await().items
                    for (fileRef in files) {
                        try {
                            val url = fileRef.downloadUrl.await().toString()
                            val metadata = fileRef.metadata.await()
                            val time = metadata.updatedTimeMillis.takeIf { it > 0 } ?:
                                metadata.creationTimeMillis
                            photosWithTime.add(Pair(url, time))
                        } catch (error: Exception) {
                            Log.e("FirebasePhotoRepo", "Could not get photo metadata", error)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            Log.d("FirebasePhotoRepo", "Listing other users' folders restricted by rules")
        }

        photosWithTime.sortedByDescending { it.second }.map { it.first }.distinct()
    } catch (error: Exception) {
        Log.e("FirebasePhotoRepo", "Could not list photos from Firebase Storage", error)
        emptyList()
    }
}
