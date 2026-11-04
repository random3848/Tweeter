package edu.csuci.tweeter.ui.post

import edu.csuci.tweeter.data.UserProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Shares a post: uploads its photo while looking up its author, then adds the post once both are
 * done. Returns the uploaded photo's URL.
 *
 * A step's failure cancels the other and is rethrown with its type unchanged, so callers can tell
 * a failed upload from a failed lookup or save. With nobody logged in there's no author, so it
 * cancels the upload and throws [NobodyLoggedInException].
 *
 * @param uploadPhoto Uploads the post's photo and returns its URL.
 * @param findAuthor Returns the profile the post is by, or null when nobody is logged in.
 * @param addPost Adds the post with the uploaded photo's URL and its author.
 */
internal suspend fun sharePost(
    uploadPhoto: suspend () -> String,
    findAuthor: suspend () -> UserProfile?,
    addPost: suspend (photoUrl: String, author: UserProfile) -> Unit
): String = coroutineScope {
    val upload = async { uploadPhoto() }
    val author = findAuthor() ?: throw NobodyLoggedInException()
    val photoUrl = upload.await()
    addPost(photoUrl, author)
    photoUrl
}

/** Thrown by [sharePost] when nobody is logged in, so the post has no author. */
internal class NobodyLoggedInException : IllegalStateException("Nobody is logged in")
