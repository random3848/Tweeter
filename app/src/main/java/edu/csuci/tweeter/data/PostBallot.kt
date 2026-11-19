package edu.csuci.tweeter.data

/**
 * The logged-in user's vote on what animal one post shows, from which follow the changes they can
 * make to it.
 *
 * @property postId The post's ID.
 * @property round The post's current [Post.voteRound], which a new vote is cast in.
 * @property userVote The animal the user's vote of [round] names, or null if they have none in it
 *     ([countedVoteOn]).
 */
data class PostBallot(val postId: String, val round: Int, val userVote: String?) {
    /** Whether the user's vote names [animal]. */
    fun agreesWith(animal: String): Boolean = userVote == animal

    /** Whether the user voted for another animal than [animal]. */
    fun disagreesWith(animal: String): Boolean = userVote != null && userVote != animal

    /**
     * Returns the change that votes for [animal] instead of [userVote], or that takes the vote back
     * if it already names [animal]: what tapping a highlighted vote control again does.
     */
    fun toggling(animal: String): VoteChange =
        if (agreesWith(animal)) changeOfVoteTo(null) else changeOfVoteTo(animal)

    /** Returns the change that votes for [animal] instead, or null if the vote already names it. */
    fun changeTo(animal: String): VoteChange? =
        if (agreesWith(animal)) null else changeOfVoteTo(animal)

    /** Returns the change that takes the user's vote back, or null if they have none. */
    fun withdrawal(): VoteChange? = if (userVote == null) null else changeOfVoteTo(null)

    // The change from the user's vote to one for [newAnimal], or to none if it's null.
    private fun changeOfVoteTo(newAnimal: String?): VoteChange =
        VoteChange(postId, round, previousAnimal = userVote, newAnimal = newAnimal)
}
