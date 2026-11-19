package edu.csuci.tweeter.data

/**
 * A user's vote on what animal one post shows.
 *
 * @property animal The animal the user voted for.
 * @property round The post's [Post.voteRound] when they voted. The vote counts only while the post
 *     is still in that round: the poster's reclassifying the post starts the next one.
 */
data class AnimalVote(val animal: String, val round: Int)

/**
 * Returns the animal of the vote in these votes, by post ID, on the post with [postId] that counts
 * in its current [round] ([Post.voteRound]), or null if there is none.
 */
fun Map<String, AnimalVote>.countedVoteOn(postId: String, round: Int): String? =
    this[postId]?.takeIf { vote -> vote.round == round }?.animal
