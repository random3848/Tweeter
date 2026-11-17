package edu.csuci.tweeter.data

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred

/**
 * A [PhotoRepository] for tests that uploads profile photos to [UPLOADED_URL] and records the
 * photos it's asked to delete. Post photos aren't uploaded.
 */
class FakePhotoRepository : PhotoRepository {
    /** Whether uploading fails, as when the photo can't be read. */
    var failsToUpload = false

    /** The URI of the profile photo uploaded last, or null if none was. */
    var uploadedPhotoUri: String? = null
        private set

    /** The URLs of the photos deleted, in order. */
    val deletedPhotoUrls = mutableListOf<String>()

    /** When set, uploading waits for it, like a slow upload. */
    var uploadFinished: CompletableDeferred<Unit>? = null

    override suspend fun uploadPhoto(photoUri: String): String =
        error("Post photos aren't uploaded in these tests")

    override suspend fun uploadProfilePhoto(photoUri: String): String {
        uploadFinished?.await()
        if (failsToUpload) {
            throw PhotoUploadException(IOException("Could not upload the photo"))
        }
        uploadedPhotoUri = photoUri
        return UPLOADED_URL
    }

    override suspend fun deletePhoto(photoUrl: String) {
        deletedPhotoUrls += photoUrl
    }

    companion object {
        /** Where every profile photo is uploaded to. */
        const val UPLOADED_URL = "https://example.com/uploaded.jpg"
    }
}
