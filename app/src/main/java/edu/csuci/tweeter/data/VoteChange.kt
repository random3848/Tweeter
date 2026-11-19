package edu.csuci.tweeter.data

/**
 * The most animals a post's [Post.voteCounts] can hold, so that voters can't grow them without
 * bound by moving their votes through ever more names. The Firestore rules (`maxVotedAnimals()`)
 * hold the same limit.
 */
const val MAX_VOTED_ANIMALS = 50

/**
 * A change to the logged-in user's vote on what animal one post shows, which moves the vote
 * between the post's [Post.voteCounts] ([voteCountsAfter]). The voting screens make them from the
 * user's [PostBallot].
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
}

/**
 * Returns the vote counts a post with [voteCounts] has after [change]: its [VoteChange.newAnimal]
 * gains a vote, and its [VoteChange.previousAnimal] loses one, and is left out once it has none,
 * so that the counts hold only animals someone votes for.
 *
 * @throws VoteException If the change adds an animal to counts that already hold
 *     [MAX_VOTED_ANIMALS] of them.
 */
fun voteCountsAfter(change: VoteChange, voteCounts: Map<String, Int>): Map<String, Int> {
    val countsAfter = voteCounts.toMutableMap()
    change.previousAnimal?.let { animal -> countsAfter.takeVoteFrom(animal) }
    change.newAnimal?.let { animal -> countsAfter.giveVoteTo(animal) }
    val addsAnimalPastCap =
        countsAfter.size > MAX_VOTED_ANIMALS && countsAfter.size > voteCounts.size
    if (addsAnimalPastCap) {
        throw VoteException(
            IllegalStateException(
                "Post ${change.postId} already has votes for $MAX_VOTED_ANIMALS animals, the " +
                    "most it can hold, so it can't get one for ${change.newAnimal}"
            )
        )
    }
    return countsAfter
}

// Takes one vote from [animal], and the animal out once no vote is left for it.
private fun MutableMap<String, Int>.takeVoteFrom(animal: String) {
    val votesLeft = getOrDefault(animal, 0) - 1
    if (votesLeft > 0) {
        this[animal] = votesLeft
    } else {
        remove(animal)
    }
}

private fun MutableMap<String, Int>.giveVoteTo(animal: String) {
    this[animal] = getOrDefault(animal, 0) + 1
}
