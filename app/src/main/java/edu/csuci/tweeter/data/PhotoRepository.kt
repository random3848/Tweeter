package edu.csuci.tweeter.data

import android.net.Uri

/** Uploads photo files independently of where post details are stored. */
interface PhotoRepository {
    /**
     * Uploads [uri] and returns the uploaded photo's HTTPS download URL.
     *
     * Throws an exception if authentication or uploading fails.
     */
    suspend fun uploadPhoto(uri: Uri): String

    /**
     * Fetches HTTPS download URLs of all photos stored in Firebase Storage, newest first.
     *
     * Returns an empty list if no photos are found or if fetching fails.
     */
    suspend fun fetchPhotoUrls(): List<String>
}
