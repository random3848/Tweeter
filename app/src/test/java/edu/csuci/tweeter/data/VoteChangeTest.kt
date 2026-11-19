package edu.csuci.tweeter.data

import org.junit.Assert.assertEquals
import org.junit.Test

private const val POST_ID = "post"
private const val ROUND = 2
private const val RACCOON = "Raccoon"
private const val POSSUM = "Possum"
private const val COYOTE = "Coyote"

class VoteChangeTest {
    @Test
    fun voteCountsAfter_newVote_addsAnimalWithOneVote() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = null, newAnimal = POSSUM)

        val counts = voteCountsAfter(change, mapOf(RACCOON to 1))

        assertEquals(mapOf(RACCOON to 1, POSSUM to 1), counts)
    }

    @Test
    fun voteCountsAfter_newVoteForAnimalWithVotes_addsOneToIt() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = null, newAnimal = POSSUM)

        assertEquals(mapOf(POSSUM to 3), voteCountsAfter(change, mapOf(POSSUM to 2)))
    }

    @Test
    fun voteCountsAfter_withdrawnVote_takesOneFromItsAnimal() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = POSSUM, newAnimal = null)

        assertEquals(mapOf(POSSUM to 1), voteCountsAfter(change, mapOf(POSSUM to 2)))
    }

    @Test
    fun voteCountsAfter_withdrawnLastVote_removesItsAnimal() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = POSSUM, newAnimal = null)

        val counts = voteCountsAfter(change, mapOf(POSSUM to 1, RACCOON to 1))

        assertEquals(mapOf(RACCOON to 1), counts)
    }

    @Test
    fun voteCountsAfter_changedVote_movesOneBetweenAnimals() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = RACCOON, newAnimal = POSSUM)

        val counts = voteCountsAfter(change, mapOf(RACCOON to 2, POSSUM to 1))

        assertEquals(mapOf(RACCOON to 1, POSSUM to 2), counts)
    }

    @Test
    fun voteCountsAfter_changedLastVote_removesPreviousAnimal() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = RACCOON, newAnimal = POSSUM)

        assertEquals(mapOf(POSSUM to 1), voteCountsAfter(change, mapOf(RACCOON to 1)))
    }

    @Test(expected = VoteException::class)
    fun voteCountsAfter_newAnimalAtCap_throws() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = null, newAnimal = POSSUM)

        voteCountsAfter(change, countsOfOtherAnimals(MAX_VOTED_ANIMALS))
    }

    @Test
    fun voteCountsAfter_newAnimalBelowCap_addsIt() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = null, newAnimal = POSSUM)

        val counts = voteCountsAfter(change, countsOfOtherAnimals(MAX_VOTED_ANIMALS - 1))

        assertEquals(MAX_VOTED_ANIMALS, counts.size)
    }

    @Test
    fun voteCountsAfter_animalWithVotesAtCap_addsOneToIt() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = null, newAnimal = POSSUM)
        val countsAtCap = countsOfOtherAnimals(MAX_VOTED_ANIMALS - 1) + (POSSUM to 1)

        assertEquals(2, voteCountsAfter(change, countsAtCap)[POSSUM])
    }

    @Test
    fun voteCountsAfter_lastVoteMovedToNewAnimalAtCap_movesIt() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = RACCOON, newAnimal = POSSUM)
        val countsAtCap = countsOfOtherAnimals(MAX_VOTED_ANIMALS - 1) + (RACCOON to 1)

        val counts = voteCountsAfter(change, countsAtCap)

        assertEquals(countsOfOtherAnimals(MAX_VOTED_ANIMALS - 1) + (POSSUM to 1), counts)
    }

    @Test(expected = VoteException::class)
    fun voteCountsAfter_oneOfSeveralVotesMovedToNewAnimalAtCap_throws() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = RACCOON, newAnimal = POSSUM)
        val countsAtCap = countsOfOtherAnimals(MAX_VOTED_ANIMALS - 1) + (RACCOON to 2)

        voteCountsAfter(change, countsAtCap)
    }

    @Test
    fun voteCountsAfter_withdrawnVotePastCap_takesItBack() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = COYOTE, newAnimal = null)
        val countsPastCap = countsOfOtherAnimals(MAX_VOTED_ANIMALS) + (COYOTE to 2)

        assertEquals(1, voteCountsAfter(change, countsPastCap)[COYOTE])
    }

    @Test(expected = IllegalArgumentException::class)
    fun init_sameAnimalBeforeAndAfter_throws() {
        VoteChange(POST_ID, ROUND, previousAnimal = POSSUM, newAnimal = POSSUM)
    }

    @Test(expected = IllegalArgumentException::class)
    fun init_noVoteBeforeOrAfter_throws() {
        VoteChange(POST_ID, ROUND, previousAnimal = null, newAnimal = null)
    }

    // The counts of [animalCount] animals other than this test's named ones, one vote each.
    private fun countsOfOtherAnimals(animalCount: Int): Map<String, Int> =
        (1..animalCount).associate { number -> "Animal $number" to 1 }
}
