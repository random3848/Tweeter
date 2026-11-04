package edu.csuci.tweeter.data

/** Uploads and deletes photos independently of where post details and profiles are stored. */
interface PhotoRepository {
    /**
     * Uploads the photo at [photoUri] for a post by the logged-in user, without the tags that say
     * where it was taken, and returns its HTTPS download URL.
     *
     * @param photoUri A `content://` or `file://` URI, as text so callers can keep it in saved
     *     state.
     * @throws PhotoUploadException If nobody is logged in, or the photo can't be read or uploaded.
     */
    suspend fun uploadPhoto(photoUri: String): String

    /**
     * Uploads the photo at [photoUri] as the logged-in user's profile photo, without the tags that
     * say where it was taken, and returns its HTTPS download URL.
     *
     * @param photoUri A `content://` or `file://` URI, as text so callers can keep it in saved
     *     state.
     * @throws PhotoUploadException If nobody is logged in, or the photo can't be read or uploaded.
     */
    suspend fun uploadProfilePhoto(photoUri: String): String

    /**
     * Deletes the uploaded photo at [photoUrl], a URL returned by one of the uploads.
     *
     * @throws PhotoDeletionException If it can't be deleted, such as when nobody is logged in, or
     *     [photoUrl] isn't the URL of an uploaded photo.
     */
    suspend fun deletePhoto(photoUrl: String)
}

/**
 * Deletes the uploaded photo at [photoUrl], which nothing uses, as far as it can, without
 * throwing: see [cleanUpUnusedPhoto]. For cleaning up after a post or profile couldn't be saved.
 */
suspend fun PhotoRepository.deleteUnusedPhoto(photoUrl: String) {
    cleanUpUnusedPhoto { deletePhoto(photoUrl) }
}
