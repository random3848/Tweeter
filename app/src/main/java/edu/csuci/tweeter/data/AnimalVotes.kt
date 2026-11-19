package edu.csuci.tweeter.data

// How the votes on what animal a post shows decide it. The poster's animal ([Post.animalType])
// is the poster's vote unless it's blank, and every other user's vote is in the post's vote
// counts ([Post.voteCounts]), each user's for one animal.

/**
 * The share of all votes on a post's animal that another animal than the poster's needs more
 * than, for the post to show it instead: a majority. At exactly this share, the post keeps the
 * poster's animal.
 */
const val SWITCHING_SHARE = 0.5

/** The agreement on a post's animal ([agreementOf]) when every vote names the animal it shows. */
const val FULL_AGREEMENT = 1.0

/**
 * Returns how many votes [animal] has on a post of [posterAnimal] with [voteCounts]: its count,
 * and the poster's vote if it's theirs.
 */
fun votesFor(animal: String, posterAnimal: String, voteCounts: Map<String, Int>): Int {
    val posterVote = if (animal == posterAnimal) posterVoteOf(posterAnimal) else 0
    return voteCounts.getOrDefault(animal, 0) + posterVote
}

/** Returns how many votes a post of [posterAnimal] with [voteCounts] has, the poster's included. */
fun totalVotesOf(posterAnimal: String, voteCounts: Map<String, Int>): Int =
    voteCounts.values.sum() + posterVoteOf(posterAnimal)

/**
 * Returns the animal a post of [posterAnimal] with [voteCounts] shows: [posterAnimal], unless
 * another animal has more than [SWITCHING_SHARE] of all votes, which only one can.
 */
fun shownAnimalOf(posterAnimal: String, voteCounts: Map<String, Int>): String {
    val switchingVoteCount = totalVotesOf(posterAnimal, voteCounts) * SWITCHING_SHARE
    for (animal in voteCounts.keys) {
        if (votesFor(animal, posterAnimal, voteCounts) > switchingVoteCount) {
            return animal
        }
    }
    return posterAnimal
}

/**
 * Returns the share of the votes on a post of [posterAnimal] with [voteCounts] that name
 * [shownAnimal], the animal it shows ([shownAnimalOf], which the caller has worked out already),
 * up to [FULL_AGREEMENT]. It's 0 when the post shows no animal, as when it has no votes at all.
 */
fun agreementOf(shownAnimal: String, posterAnimal: String, voteCounts: Map<String, Int>): Double {
    val totalVotes = totalVotesOf(posterAnimal, voteCounts)
    if (totalVotes == 0) {
        return 0.0
    }
    return votesFor(shownAnimal, posterAnimal, voteCounts).toDouble() / totalVotes
}

// The poster's vote for [posterAnimal]: one, unless they named no animal.
private fun posterVoteOf(posterAnimal: String): Int = if (posterAnimal.isBlank()) 0 else 1
