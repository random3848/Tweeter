package edu.csuci.tweeter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val POST_ID = "post"
private const val ROUND = 1
private const val RACCOON = "Raccoon"
private const val POSSUM = "Possum"

class PostBallotTest {
    @Test
    fun toggling_noVote_votesForAnimal() {
        assertEquals(change(from = null, to = RACCOON), ballotOf(null).toggling(RACCOON))
    }

    @Test
    fun toggling_voteForAnimal_withdrawsIt() {
        assertEquals(change(from = RACCOON, to = null), ballotOf(RACCOON).toggling(RACCOON))
    }

    @Test
    fun toggling_voteForOtherAnimal_movesItToAnimal() {
        assertEquals(change(from = POSSUM, to = RACCOON), ballotOf(POSSUM).toggling(RACCOON))
    }

    @Test
    fun changeTo_noVote_votesForAnimal() {
        assertEquals(change(from = null, to = POSSUM), ballotOf(null).changeTo(POSSUM))
    }

    @Test
    fun changeTo_voteForOtherAnimal_movesItToAnimal() {
        assertEquals(change(from = RACCOON, to = POSSUM), ballotOf(RACCOON).changeTo(POSSUM))
    }

    @Test
    fun changeTo_voteForAnimal_changesNothing() {
        assertNull(ballotOf(POSSUM).changeTo(POSSUM))
    }

    @Test
    fun withdrawal_vote_takesItBack() {
        assertEquals(change(from = POSSUM, to = null), ballotOf(POSSUM).withdrawal())
    }

    @Test
    fun withdrawal_noVote_changesNothing() {
        assertNull(ballotOf(null).withdrawal())
    }

    @Test
    fun agreesWith_voteForAnimal_true() {
        assertTrue(ballotOf(RACCOON).agreesWith(RACCOON))
    }

    @Test
    fun agreesWith_voteForOtherAnimal_false() {
        assertFalse(ballotOf(POSSUM).agreesWith(RACCOON))
    }

    @Test
    fun agreesWith_noVote_false() {
        assertFalse(ballotOf(null).agreesWith(RACCOON))
    }

    @Test
    fun disagreesWith_voteForOtherAnimal_true() {
        assertTrue(ballotOf(POSSUM).disagreesWith(RACCOON))
    }

    @Test
    fun disagreesWith_voteForAnimal_false() {
        assertFalse(ballotOf(RACCOON).disagreesWith(RACCOON))
    }

    @Test
    fun disagreesWith_noVote_false() {
        assertFalse(ballotOf(null).disagreesWith(RACCOON))
    }

    private fun ballotOf(userVote: String?) = PostBallot(POST_ID, ROUND, userVote)

    private fun change(from: String?, to: String?) =
        VoteChange(POST_ID, ROUND, previousAnimal = from, newAnimal = to)
}
