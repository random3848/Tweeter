package edu.csuci.tweeter.data

import org.junit.Assert.assertEquals
import org.junit.Test

private const val RACCOON = "Raccoon"
private const val POSSUM = "Possum"
private const val COYOTE = "Coyote"
private const val NO_ANIMAL = ""

// The meeting's example: the poster tagged a raccoon, and 99 of 100 voters picked possum.
private const val VOTERS_FOR_POSSUM = 99

private const val EXACT = 0.0

class AnimalVotesTest {
    @Test
    fun votesFor_postersAnimal_countsPostersVote() {
        assertEquals(3, votesFor(RACCOON, RACCOON, mapOf(RACCOON to 2)))
    }

    @Test
    fun votesFor_otherAnimal_countsOnlyItsVotes() {
        assertEquals(2, votesFor(POSSUM, RACCOON, mapOf(POSSUM to 2, RACCOON to 1)))
    }

    @Test
    fun votesFor_animalNobodyVotedFor_isZero() {
        assertEquals(0, votesFor(COYOTE, RACCOON, mapOf(POSSUM to 2)))
    }

    @Test
    fun votesFor_blankPosterAnimal_hasNoPosterVote() {
        assertEquals(0, votesFor(NO_ANIMAL, NO_ANIMAL, emptyMap()))
    }

    @Test
    fun totalVotesOf_countsVotesAndPostersVote() {
        assertEquals(4, totalVotesOf(RACCOON, mapOf(POSSUM to 2, RACCOON to 1)))
    }

    @Test
    fun totalVotesOf_blankPosterAnimal_countsOnlyVotes() {
        assertEquals(3, totalVotesOf(NO_ANIMAL, mapOf(POSSUM to 2, COYOTE to 1)))
    }

    @Test
    fun shownAnimalOf_noVotes_isPostersAnimal() {
        assertEquals(RACCOON, shownAnimalOf(RACCOON, emptyMap()))
    }

    @Test
    fun shownAnimalOf_votesForPostersAnimal_isPostersAnimal() {
        assertEquals(RACCOON, shownAnimalOf(RACCOON, mapOf(RACCOON to 4)))
    }

    @Test
    fun shownAnimalOf_otherAnimalHasExactlyHalf_isPostersAnimal() {
        assertEquals(RACCOON, shownAnimalOf(RACCOON, mapOf(POSSUM to 1)))
    }

    @Test
    fun shownAnimalOf_otherAnimalHasMoreThanHalf_isThatAnimal() {
        assertEquals(POSSUM, shownAnimalOf(RACCOON, mapOf(POSSUM to 2)))
    }

    @Test
    fun shownAnimalOf_otherAnimalHasMostVotesButNoMajority_isPostersAnimal() {
        assertEquals(RACCOON, shownAnimalOf(RACCOON, mapOf(POSSUM to 2, COYOTE to 2)))
    }

    @Test
    fun shownAnimalOf_votesForPostersAnimalCountWithPostersVote() {
        assertEquals(RACCOON, shownAnimalOf(RACCOON, mapOf(POSSUM to 2, RACCOON to 1)))
    }

    @Test
    fun shownAnimalOf_almostEveryVoterDisagrees_isTheirAnimal() {
        val voteCounts = mapOf(POSSUM to VOTERS_FOR_POSSUM, RACCOON to 1)

        assertEquals(POSSUM, shownAnimalOf(RACCOON, voteCounts))
    }

    @Test
    fun shownAnimalOf_blankPosterAnimalWithoutVotes_isBlank() {
        assertEquals(NO_ANIMAL, shownAnimalOf(NO_ANIMAL, emptyMap()))
    }

    @Test
    fun shownAnimalOf_blankPosterAnimalAndMajority_isMajorityAnimal() {
        assertEquals(POSSUM, shownAnimalOf(NO_ANIMAL, mapOf(POSSUM to 2, COYOTE to 1)))
    }

    @Test
    fun shownAnimalOf_blankPosterAnimalAndExactlyHalf_isBlank() {
        assertEquals(NO_ANIMAL, shownAnimalOf(NO_ANIMAL, mapOf(POSSUM to 1, COYOTE to 1)))
    }

    @Test
    fun agreementOf_onlyPostersVote_isFull() {
        assertEquals(FULL_AGREEMENT, agreementOf(RACCOON, emptyMap()), EXACT)
    }

    @Test
    fun agreementOf_everyVoteForPostersAnimal_isFull() {
        assertEquals(FULL_AGREEMENT, agreementOf(RACCOON, mapOf(RACCOON to 3)), EXACT)
    }

    @Test
    fun agreementOf_someVotesForOtherAnimal_isShareOfPostersAnimal() {
        assertEquals(0.75, agreementOf(RACCOON, mapOf(RACCOON to 2, POSSUM to 1)), EXACT)
    }

    @Test
    fun agreementOf_exactlyHalfForOtherAnimal_isHalf() {
        assertEquals(0.5, agreementOf(RACCOON, mapOf(POSSUM to 1)), EXACT)
    }

    @Test
    fun agreementOf_majorityForOtherAnimal_isShareOfThatAnimal() {
        assertEquals(0.75, agreementOf(RACCOON, mapOf(POSSUM to 3)), EXACT)
    }

    @Test
    fun agreementOf_blankPosterAnimalWithoutVotes_isZero() {
        assertEquals(0.0, agreementOf(NO_ANIMAL, emptyMap()), EXACT)
    }

    @Test
    fun agreementOf_blankPosterAnimalWithoutMajority_isZero() {
        assertEquals(0.0, agreementOf(NO_ANIMAL, mapOf(POSSUM to 1, COYOTE to 1)), EXACT)
    }
}
