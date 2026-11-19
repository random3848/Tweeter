package edu.csuci.tweeter.data

/**
 * A change to the logged-in user's vote on what animal one post shows, which moves the vote
 * between the post's [Post.voteCounts]. [PostBallot] makes them.
 *
 * @property postId The post voted on.
 * @property round The post's current [Post.voteRound], which the new vote is cast in.
 * @property previousAnimal The animal the user's vote of [round] named, which loses it, or null if
 *     they had none.
 * @property newAnimal The animal the user votes for now, which gains the vote, or null if they
 *     take it back.
 * @throws IllegalArgumentException If [previousAnimal] and [newAnimal] are the same, so that every
 *     change changes some count.
 */
data class VoteChange(
    val postId: String,
    val round: Int,
    val previousAnimal: String?,
    val newAnimal: String?
) {
    init {
        require(previousAnimal != newAnimal) { "A vote change from $previousAnimal to itself" }
    }

    /** How much the change adds to the count of each animal it changes: -1 or 1. */
    val countIncrements: Map<String, Int>
        get() = buildMap {
            previousAnimal?.let { put(it, -1) }
            newAnimal?.let { put(it, 1) }
        }
}
