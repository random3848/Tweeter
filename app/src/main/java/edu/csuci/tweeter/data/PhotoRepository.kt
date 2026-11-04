package edu.csuci.tweeter.data

import android.net.Uri

/** Uploads photo files independently of where post details and profiles are stored. */
interface PhotoRepository {
    /**
     * Uploads [uri] and returns the uploaded photo's HTTPS download URL.
     *
     * @throws PhotoUploadException if authentication or uploading fails.
     */
    suspend fun uploadPhoto(uri: Uri): String

    /**
     * Uploads the photo at [photoUri] as the logged-in user's profile photo, without the tags that
     * say where it was taken, and returns its HTTPS download URL.
     *
     * @param photoUri A `content://` or `file://` URI, as text so callers can keep it in saved
     *     state.
     * @throws PhotoUploadException If nobody is logged in, or the photo can't be read or uploaded.
     */
    suspend fun uploadProfilePhoto(photoUri: String): String
}
