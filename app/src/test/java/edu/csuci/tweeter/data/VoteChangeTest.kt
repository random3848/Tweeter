package edu.csuci.tweeter.data

import org.junit.Assert.assertEquals
import org.junit.Test

private const val POST_ID = "post"
private const val ROUND = 2
private const val RACCOON = "Raccoon"
private const val POSSUM = "Possum"

class VoteChangeTest {
    @Test
    fun countIncrements_newVote_addsOneToItsAnimal() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = null, newAnimal = POSSUM)

        assertEquals(mapOf(POSSUM to 1), change.countIncrements)
    }

    @Test
    fun countIncrements_withdrawnVote_takesOneFromItsAnimal() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = POSSUM, newAnimal = null)

        assertEquals(mapOf(POSSUM to -1), change.countIncrements)
    }

    @Test
    fun countIncrements_changedVote_movesOneBetweenAnimals() {
        val change = VoteChange(POST_ID, ROUND, previousAnimal = RACCOON, newAnimal = POSSUM)

        assertEquals(mapOf(RACCOON to -1, POSSUM to 1), change.countIncrements)
    }

    @Test(expected = IllegalArgumentException::class)
    fun init_sameAnimalBeforeAndAfter_throws() {
        VoteChange(POST_ID, ROUND, previousAnimal = POSSUM, newAnimal = POSSUM)
    }

    @Test(expected = IllegalArgumentException::class)
    fun init_noVoteBeforeOrAfter_throws() {
        VoteChange(POST_ID, ROUND, previousAnimal = null, newAnimal = null)
    }
}
