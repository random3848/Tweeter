package edu.csuci.tweeter.data

import android.net.Uri

/** Uploads photo files independently of where post details are stored. */
interface PhotoRepository {
    /**
     * Uploads [uri] and returns the uploaded photo's HTTPS download URL.
     *
     * @throws PhotoUploadException if authentication or uploading fails.
     */
    suspend fun uploadPhoto(uri: Uri): String
}
