package edu.csuci.tweeter.ui.voting

import edu.csuci.tweeter.data.totalVotesOf
import edu.csuci.tweeter.data.votesFor

/**
 * One animal voted for on a post, as the voting page lists it.
 *
 * @property animal The animal's name.
 * @property voteCount How many voted for it, the poster included.
 * @property share Its share of all votes on the post, from 0 to 1.
 * @property isPosterChoice Whether it's the animal the poster named.
 */
data class AnimalShare(
    val animal: String,
    val voteCount: Int,
    val share: Double,
    val isPosterChoice: Boolean
)

// Orders the animals other than the poster's: the most voted for first, alphabetically among
// those with as many votes, so the order doesn't depend on how the counts were stored.
private val OTHER_ANIMAL_ORDER =
    compareByDescending<Map.Entry<String, Int>> { (_, voteCount) -> voteCount }
        .thenBy { (animal, _) -> animal }

/**
 * Returns every animal voted for on a post of [posterAnimal] with [voteCounts], with its votes:
 * the poster's first, unless it's blank, then the others, the most voted for first.
 */
fun animalSharesOf(posterAnimal: String, voteCounts: Map<String, Int>): List<AnimalShare> {
    val posterAnimals = if (posterAnimal.isBlank()) emptyList() else listOf(posterAnimal)
    val otherAnimals = voteCounts.entries
        .filter { (animal, voteCount) -> animal != posterAnimal && voteCount > 0 }
        .sortedWith(OTHER_ANIMAL_ORDER)
        .map { (animal, _) -> animal }
    val totalVotes = totalVotesOf(posterAnimal, voteCounts)
    return (posterAnimals + otherAnimals).map { animal ->
        val voteCount = votesFor(animal, posterAnimal, voteCounts)
        AnimalShare(
            animal = animal,
            voteCount = voteCount,
            share = voteCount.toDouble() / totalVotes,
            isPosterChoice = animal == posterAnimal
        )
    }
}
