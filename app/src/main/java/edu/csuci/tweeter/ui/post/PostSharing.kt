package edu.csuci.tweeter.ui.post

import edu.csuci.tweeter.data.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** The photo a post is shared with: uploaded for the post, and deleted if it can't be added. */
internal interface PostPhoto {
    /** Uploads the photo and returns its URL. */
    suspend fun upload(): String

    /**
     * Deletes the photo uploaded to [photoUrl], which nothing uses, as far as it can. Doesn't
     * throw, so the failure that left the photo unused is the one reported.
     */
    suspend fun delete(photoUrl: String)
}

/**
 * Shares a post: uploads its [photo] while looking up its author, then adds the post once both
 * are done. Returns the uploaded photo's URL.
 *
 * A step's failure cancels the other and is rethrown with its type unchanged, so callers can tell
 * a failed upload from a failed lookup or save. With nobody logged in there's no author, so it
 * cancels the upload and throws [NobodyLoggedInException].
 *
 * Whenever the photo was uploaded but the post isn't added, because a step failed or sharing
 * was cancelled before [addPost] ran, the photo is deleted again. Cancelling while [addPost] runs
 * keeps the photo, since the post may be added all the same.
 *
 * @param findAuthor Returns the profile the post is by, or null when nobody is logged in.
 * @param addPost Adds the post with the uploaded photo's URL and its author.
 */
internal suspend fun sharePost(
    photo: PostPhoto,
    findAuthor: suspend () -> UserProfile?,
    addPost: suspend (photoUrl: String, author: UserProfile) -> Unit
): String {
    val (photoUrl, author) = uploadWhileFindingAuthor(photo, findAuthor)
    addPostOrDeletePhoto(photo, photoUrl) { addPost(photoUrl, author) }
    return photoUrl
}

// Returns the uploaded photo's URL and the author. If either step fails, or this is cancelled,
// the upload is stopped, and its photo deleted if it was uploaded all the same.
private suspend fun uploadWhileFindingAuthor(
    photo: PostPhoto,
    findAuthor: suspend () -> UserProfile?
): Pair<String, UserProfile> = coroutineScope {
    val upload = async { photo.upload() }
    try {
        upload.awaitWithAuthor(findAuthor)
    } catch (error: Throwable) {
        withContext(NonCancellable) { photo.deleteUpload(upload) }
        throw error
    }
}

private suspend fun Deferred<String>.awaitWithAuthor(
    findAuthor: suspend () -> UserProfile?
): Pair<String, UserProfile> {
    val author = findAuthor() ?: throw NobodyLoggedInException()
    return await() to author
}

// Stops [upload] and deletes its photo if it finished uploading anyway.
private suspend fun PostPhoto.deleteUpload(upload: Deferred<String>) {
    upload.cancelAndJoin()
    val isUploaded = !upload.isCancelled
    if (isUploaded) {
        delete(upload.await())
    }
}

private suspend fun addPostOrDeletePhoto(
    photo: PostPhoto,
    photoUrl: String,
    addPost: suspend () -> Unit
) {
    try {
        addPost()
    } catch (error: CancellationException) {
        // A post being added can't be taken back by cancelling, so it may still need its photo.
        throw error
    } catch (error: Throwable) {
        withContext(NonCancellable) { photo.delete(photoUrl) }
        throw error
    }
}

/** Thrown by [sharePost] when nobody is logged in, so the post has no author. */
internal class NobodyLoggedInException : IllegalStateException("Nobody is logged in")
