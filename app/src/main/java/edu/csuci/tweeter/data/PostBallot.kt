package edu.csuci.tweeter.data

/**
 * The logged-in user's vote on what animal one post shows, as a screen shows it, which a change
 * to the vote is worked out from.
 *
 * @property postId The post's ID.
 * @property round The post's current [Post.voteRound], which a new vote is cast in.
 * @property userVote The animal the user's vote of [round] names, or null if they have none in it
 *     ([countedVoteOn]).
 */
data class PostBallot(val postId: String, val round: Int, val userVote: String?)
