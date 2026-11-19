package edu.csuci.tweeter.ui.feed

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * The posts whose change, such as a like or a vote, is being saved, for the controls on each post
 * that ignore taps until its last change is saved.
 *
 * A screen reads whether the user liked, saved or voted on a post from a Firestore listener, which
 * shows a change only once it reports it, so a second tap before then would be worked out from the
 * post as it was before the first.
 */
internal class PendingPostChanges {
    // The IDs of the posts whose change the server hasn't saved yet.
    private val postIds = MutableStateFlow(emptySet<String>())

    /**
     * Runs [save] for the post with [postId], unless a change to the post is still being saved.
     * Whatever [save] throws is thrown on, once the post takes changes again.
     */
    suspend fun saveUnlessPending(postId: String, save: suspend () -> Unit) {
        val isPending = postId in postIds.getAndUpdate { it + postId }
        if (isPending) {
            return
        }
        savePending(postId, save)
    }

    // Runs [save] for the post with [postId], then lets the post take changes again.
    private suspend fun savePending(postId: String, save: suspend () -> Unit) {
        try {
            save()
        } finally {
            postIds.update { it - postId }
        }
    }
}
