package edu.csuci.tweeter.ui.voting

import android.util.Log
import edu.csuci.tweeter.data.AnimalVote
import edu.csuci.tweeter.data.VoteChange
import edu.csuci.tweeter.data.VoteException
import edu.csuci.tweeter.data.VoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

private const val TAG = "PostVoter"

/**
 * Saves the user's votes on what animal posts show, and the poster's reclassifying of their own,
 * for a screen that shows the votes, such as Feed or the voting page, each screen holding its own.
 *
 * A post's vote controls ignore taps while the last change to its votes is being saved: a screen
 * reads the user's vote from [userVotes], which shows the change only once Firestore's listener
 * reports it, so a second tap before then would be worked out from the vote before the first.
 *
 * @param voteRepository The user's votes and the posts' vote counts, and where changes are saved.
 */
class PostVoter(private val voteRepository: VoteRepository) {
    // The IDs of the posts whose votes have a change the server hasn't saved yet.
    private val postsWithVotesChanging = MutableStateFlow(emptySet<String>())

    /** The user's votes, by post ID, updated as they vote or log in or out. */
    fun userVotes(): Flow<Map<String, AnimalVote>> = voteRepository.getVotes()

    /** The vote counts of the post with [postId] ([VoteRepository.getVoteCounts]). */
    fun voteCounts(postId: String): Flow<Map<String, Int>> = voteRepository.getVoteCounts(postId)

    /**
     * Saves [change] to the user's vote, unless a change to the votes on its post is still being
     * saved. A change that can't be saved is logged; the post then shows the vote as it was.
     */
    suspend fun changeVote(change: VoteChange) {
        changeVotes(change.postId) { voteRepository.changeVote(change) }
    }

    /**
     * Has the user's post with [postId] name [animal] instead, which clears its votes, unless a
     * change to its votes is still being saved. One that can't be saved is logged; the post then
     * keeps its animal.
     */
    suspend fun reclassify(postId: String, animal: String) {
        changeVotes(postId) { voteRepository.reclassify(postId, animal) }
    }

    // Saves [change] to the votes on the post with [postId], unless another is still being saved.
    private suspend fun changeVotes(postId: String, change: suspend () -> Unit) {
        val isAlreadyChanging = postId in postsWithVotesChanging.getAndUpdate { it + postId }
        if (isAlreadyChanging) {
            return
        }
        saveVotesChange(postId, change)
    }

    // Saves [change] to the votes on the post with [postId], then lets its controls take taps
    // again.
    private suspend fun saveVotesChange(postId: String, change: suspend () -> Unit) {
        try {
            change()
        } catch (error: VoteException) {
            Log.e(TAG, "Could not change the votes on post $postId", error)
        } finally {
            postsWithVotesChanging.update { it - postId }
        }
    }
}
