package edu.csuci.tweeter.data

import android.net.Uri
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import edu.csuci.tweeter.location.LocationTagRemover
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

// The Storage folders photos go in, each with a subfolder per user. The rules in storage.rules
// match these names.
private const val POST_PHOTOS = "images"
private const val PROFILE_PHOTOS = "profile_photos"

/**
 * Uploads JPEGs to the logged-in user's folders in Firebase Storage, and deletes them again.
 *
 * Guests can only browse, so the Storage rules refuse uploads and deletes from anonymous
 * sessions.
 *
 * @param locationTagRemover Removes location tags from photos before they're uploaded.
 */
class FirebasePhotoRepository(
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val locationTagRemover: LocationTagRemover
) : PhotoRepository {
    override suspend fun uploadPhoto(photoUri: String): String =
        uploadWithoutLocation(photoUri, POST_PHOTOS)

    override suspend fun uploadProfilePhoto(photoUri: String): String =
        uploadWithoutLocation(photoUri, PROFILE_PHOTOS)

    override suspend fun deletePhoto(photoUrl: String) {
        storedPhotoAt(photoUrl).deleteStored()
    }

    // Uploads a copy of the photo at [photoUri] without its location tags into [folder], and
    // deletes the copy once the upload is over.
    private suspend fun uploadWithoutLocation(photoUri: String, folder: String): String {
        val copy = copyWithoutLocation(photoUri)
        try {
            return upload(Uri.fromFile(copy), folder)
        } finally {
            locationTagRemover.deleteCopy(copy)
        }
    }

    private suspend fun copyWithoutLocation(photoUri: String): File = try {
        locationTagRemover.copyWithoutLocation(Uri.parse(photoUri))
    } catch (error: IOException) {
        throw PhotoUploadException(error)
    } catch (error: SecurityException) {
        // The app can lose its access to a photo picked from the camera roll, such as when
        // Android stops it in the background and the screen is restored later.
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

            return photoReference.putFileAndGetUrl(uri, metadata)
        } catch (error: FirebaseException) {
            throw PhotoUploadException(error)
        } catch (error: IOException) {
            throw PhotoUploadException(error)
        } catch (error: SecurityException) {
            throw PhotoUploadException(error)
        }
    }

    // Uploads [uri] here and returns its download URL. A photo that was uploaded but whose URL
    // couldn't be fetched, or stopped being waited for, is deleted again: nothing could show it.
    private suspend fun StorageReference.putFileAndGetUrl(
        uri: Uri,
        metadata: StorageMetadata
    ): String {
        val upload = putFile(uri, metadata)
        try {
            upload.awaitOrCancel()
            return downloadUrl.await().toString()
        } catch (error: Throwable) {
            deleteIfStored(upload)
            throw error
        }
    }

    // Waits for the upload. Cancelling the wait, such as by leaving Edit profile, cancels the
    // upload too; await() alone would let it finish and leave a photo nothing uses.
    private suspend fun UploadTask.awaitOrCancel() {
        try {
            await()
        } catch (error: CancellationException) {
            cancel()
            throw error
        }
    }

    // Deletes the photo [upload] put here, if it got here. This cleans up after a failure or
    // cancellation, so it doesn't throw.
    private suspend fun StorageReference.deleteIfStored(upload: UploadTask) {
        if (upload.isSuccessful) {
            cleanUpUnusedPhoto { deleteStored() }
        }
    }

    // The reference to the stored photo whose download URL is [photoUrl].
    private fun storedPhotoAt(photoUrl: String): StorageReference = try {
        storage.getReferenceFromUrl(photoUrl)
    } catch (error: IllegalArgumentException) {
        // Thrown for a URL that isn't one of this project's Storage URLs.
        throw PhotoDeletionException(error)
    }

    private suspend fun StorageReference.deleteStored() {
        try {
            delete().await()
        } catch (error: FirebaseException) {
            throw PhotoDeletionException(error)
        }
    }
}
