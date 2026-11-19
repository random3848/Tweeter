package edu.csuci.tweeter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val POST_ID = "post"
private const val OTHER_POST_ID = "other"
private const val ROUND = 3
private const val EARLIER_ROUND = 2
private const val POSSUM = "Possum"

class AnimalVoteTest {
    @Test
    fun countedVoteOn_voteOfPostsRound_isItsAnimal() {
        val votes = mapOf(POST_ID to AnimalVote(POSSUM, ROUND))

        assertEquals(POSSUM, votes.countedVoteOn(POST_ID, ROUND))
    }

    @Test
    fun countedVoteOn_voteOfEarlierRound_isNull() {
        val votes = mapOf(POST_ID to AnimalVote(POSSUM, EARLIER_ROUND))

        assertNull(votes.countedVoteOn(POST_ID, ROUND))
    }

    @Test
    fun countedVoteOn_voteOnOtherPostOnly_isNull() {
        val votes = mapOf(OTHER_POST_ID to AnimalVote(POSSUM, ROUND))

        assertNull(votes.countedVoteOn(POST_ID, ROUND))
    }
}
