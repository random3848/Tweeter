package edu.csuci.tweeter.ui.voting

import edu.csuci.tweeter.data.agreementOf
import edu.csuci.tweeter.data.shownAnimalOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val RACCOON = "Raccoon"
private const val POSSUM = "Possum"
private const val COYOTE = "Coyote"
private const val BOBCAT = "Bobcat"
private const val NO_ANIMAL = ""

class IdentificationTest {
    @Test
    fun of_unknownPostWithoutVotes_isUnknown() {
        assertEquals(Identification.UNKNOWN, identificationOf(NO_ANIMAL, emptyMap()))
    }

    @Test
    fun of_unknownPostWithTie_isUnknown() {
        val voteCounts = mapOf(POSSUM to 1, COYOTE to 1, BOBCAT to 1)

        assertEquals(Identification.UNKNOWN, identificationOf(NO_ANIMAL, voteCounts))
    }

    @Test
    fun of_unknownPostWithExactlyHalfForOneAnimal_isUnknown() {
        val voteCounts = mapOf(POSSUM to 2, COYOTE to 1, BOBCAT to 1)

        assertEquals(Identification.UNKNOWN, identificationOf(NO_ANIMAL, voteCounts))
    }

    @Test
    fun of_unknownPostWithMajorityButNotEveryVote_isDisputed() {
        val voteCounts = mapOf(POSSUM to 2, COYOTE to 1)

        assertEquals(Identification.DISPUTED, identificationOf(NO_ANIMAL, voteCounts))
    }

    @Test
    fun of_unknownPostWithEveryVoteForOneAnimal_isAgreed() {
        assertEquals(Identification.AGREED, identificationOf(NO_ANIMAL, mapOf(POSSUM to 2)))
    }

    @Test
    fun of_namedPostWithoutVotes_isAgreed() {
        assertEquals(Identification.AGREED, identificationOf(RACCOON, emptyMap()))
    }

    @Test
    fun of_namedPostWithExactlyHalfForOtherAnimal_isDisputed() {
        assertEquals(Identification.DISPUTED, identificationOf(RACCOON, mapOf(POSSUM to 1)))
    }

    @Test
    fun hasShownAnimal_unknown_isFalse() {
        assertFalse(Identification.UNKNOWN.hasShownAnimal)
    }

    @Test
    fun hasShownAnimal_agreedOrDisputed_isTrue() {
        assertTrue(Identification.AGREED.hasShownAnimal && Identification.DISPUTED.hasShownAnimal)
    }

    // How sure everyone is of a post of [posterAnimal] with [voteCounts], as its card works it out
    // from its table row.
    private fun identificationOf(posterAnimal: String, voteCounts: Map<String, Int>) =
        Identification.of(
            shownAnimal = shownAnimalOf(posterAnimal, voteCounts),
            agreement = agreementOf(posterAnimal, voteCounts)
        )
}
