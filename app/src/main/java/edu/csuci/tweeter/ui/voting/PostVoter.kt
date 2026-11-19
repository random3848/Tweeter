package edu.csuci.tweeter.ui.voting

import android.util.Log
import edu.csuci.tweeter.data.AnimalVote
import edu.csuci.tweeter.data.PostBallot
import edu.csuci.tweeter.data.VoteChange
import edu.csuci.tweeter.data.VoteException
import edu.csuci.tweeter.data.VoteRepository
import edu.csuci.tweeter.ui.feed.PendingPostChanges
import kotlinx.coroutines.flow.Flow

private const val TAG = "PostVoter"

/**
 * Saves the user's votes on what animal posts show, and the poster's reclassifying of their own,
 * for a screen that shows the votes, such as Feed or the voting page, each screen holding its own.
 *
 * A post's vote controls ignore taps while the last change to its votes is being saved, as
 * [PendingPostChanges] explains.
 *
 * @param voteRepository The user's votes and the posts' vote counts, and where changes are saved.
 */
class PostVoter(private val voteRepository: VoteRepository) {
    private val pendingChanges = PendingPostChanges()

    /** The user's votes, by post ID, updated as they vote or log in or out. */
    fun userVotes(): Flow<Map<String, AnimalVote>> = voteRepository.getVotes()

    /** The vote counts of the post with [postId] ([VoteRepository.getVoteCounts]). */
    fun voteCounts(postId: String): Flow<Map<String, Int>> = voteRepository.getVoteCounts(postId)

    /**
     * Votes for [animal] instead of the [ballot]'s vote, or takes the vote back if it already
     * names [animal], as tapping a highlighted vote control again does, unless a change to the
     * votes on the post is still being saved. A change that can't be saved is logged; the post
     * then shows the vote as it was.
     */
    suspend fun toggleVote(ballot: PostBallot, animal: String) {
        val change = changeToggling(ballot, animal)
        changeVotes(ballot.postId) { voteRepository.changeVote(change) }
    }

    // The change from the [ballot]'s vote to one for [animal], or to none if it names [animal].
    private fun changeToggling(ballot: PostBallot, animal: String): VoteChange {
        val newAnimal = if (ballot.userVote == animal) null else animal
        return VoteChange(
            ballot.postId,
            ballot.round,
            previousAnimal = ballot.userVote,
            newAnimal = newAnimal
        )
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
        pendingChanges.saveUnlessPending(postId) { saveVotesChange(postId, change) }
    }

    // Saves [change] to the votes on the post with [postId], logging a change that can't be saved.
    private suspend fun saveVotesChange(postId: String, change: suspend () -> Unit) {
        try {
            change()
        } catch (error: VoteException) {
            Log.e(TAG, "Could not change the votes on post $postId", error)
        }
    }
}
