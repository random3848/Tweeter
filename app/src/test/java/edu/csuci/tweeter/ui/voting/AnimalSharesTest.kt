package edu.csuci.tweeter.ui.voting

import org.junit.Assert.assertEquals
import org.junit.Test

private const val RACCOON = "Raccoon"
private const val POSSUM = "Possum"
private const val COYOTE = "Coyote"
private const val BOBCAT = "Bobcat"

class AnimalSharesTest {
    @Test
    fun animalSharesOf_postersAnimalWithFewestVotes_comesFirst() {
        val shares = animalSharesOf(RACCOON, mapOf(POSSUM to 3))

        assertEquals(listOf(RACCOON, POSSUM), shares.map { it.animal })
    }

    @Test
    fun animalSharesOf_otherAnimals_mostVotesFirstThenByName() {
        val shares = animalSharesOf(RACCOON, mapOf(COYOTE to 1, POSSUM to 2, BOBCAT to 1))

        assertEquals(listOf(RACCOON, POSSUM, BOBCAT, COYOTE), shares.map { it.animal })
    }

    @Test
    fun animalSharesOf_postersAnimal_countsPostersVoteWithOthers() {
        val shares = animalSharesOf(RACCOON, mapOf(RACCOON to 2, POSSUM to 1))

        val expected = AnimalShare(RACCOON, voteCount = 3, share = 0.75, isPosterChoice = true)
        assertEquals(expected, shares[0])
    }

    @Test
    fun animalSharesOf_otherAnimal_isShareOfAllVotes() {
        val shares = animalSharesOf(RACCOON, mapOf(RACCOON to 2, POSSUM to 1))

        val expected = AnimalShare(POSSUM, voteCount = 1, share = 0.25, isPosterChoice = false)
        assertEquals(expected, shares[1])
    }

    @Test
    fun animalSharesOf_noVotes_listsOnlyPostersAnimal() {
        val shares = animalSharesOf(RACCOON, emptyMap())

        assertEquals(listOf(AnimalShare(RACCOON, 1, share = 1.0, isPosterChoice = true)), shares)
    }

    @Test
    fun animalSharesOf_blankPosterAnimal_listsOnlyVotedAnimals() {
        val shares = animalSharesOf("", mapOf(POSSUM to 1))

        assertEquals(listOf(AnimalShare(POSSUM, 1, share = 1.0, isPosterChoice = false)), shares)
    }

    @Test
    fun animalSharesOf_blankPosterAnimalWithoutVotes_listsNothing() {
        assertEquals(emptyList<AnimalShare>(), animalSharesOf("", emptyMap()))
    }
}
