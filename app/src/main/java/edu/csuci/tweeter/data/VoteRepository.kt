package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow

/**
 * The votes on what animal posts show: the logged-in user's own, which also change the posts'
 * [Post.voteCounts], each post's counts, and the poster's reclassifying of their post, which
 * starts its votes over.
 *
 * Guests can only look: they have voted on no posts, and can't vote or reclassify.
 */
interface VoteRepository {
    /**
     * The logged-in user's votes, by the ID of the post each is on, or none while nobody is logged
     * in. A vote counts only in the [AnimalVote.round] it was cast in. Emits again whenever the
     * user changes a vote, or someone else logs in.
     */
    fun getVotes(): Flow<Map<String, AnimalVote>>

    /**
     * The [Post.voteCounts] of the post with [postId], as they change; none once it's deleted. For
     * a screen that shows one post's votes, since a [PostTable] keeps only what they mean.
     */
    fun getVoteCounts(postId: String): Flow<Map<String, Int>>

    /**
     * Saves [change] to the logged-in user's vote, and moves the vote between the post's counts.
     *
     * @throws VoteException If the change can't be saved, such as when nobody is logged in or the
     *     user shared the post.
     */
    suspend fun changeVote(change: VoteChange)

    /**
     * Has the logged-in user's post with [postId] name [animal] instead, which takes away its votes
     * and its confidence ([Post.confidence]) and starts its next [Post.voteRound].
     *
     * @throws VoteException If it can't be saved, such as when the user didn't share the post or
     *     it already names [animal].
     */
    suspend fun reclassify(postId: String, animal: String)
}
