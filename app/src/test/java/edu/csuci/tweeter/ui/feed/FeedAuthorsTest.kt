package edu.csuci.tweeter.ui.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FOLLOWED_UID = "jordan"
private const val STRANGER_UID = "sam"

class FeedAuthorsTest {
    @Test
    fun leavesOut_everyone_leavesNoneOut() {
        assertFalse(FeedAuthors.Everyone.leavesOut(STRANGER_UID))
    }

    @Test
    fun leavesOut_followedUser_keepsThem() {
        assertFalse(FeedAuthors.Followed(setOf(FOLLOWED_UID)).leavesOut(FOLLOWED_UID))
    }

    @Test
    fun leavesOut_stranger_leavesThemOut() {
        assertTrue(FeedAuthors.Followed(setOf(FOLLOWED_UID)).leavesOut(STRANGER_UID))
    }

    @Test
    fun leavesOut_whileFollowedAreRead_leavesNoneOutYet() {
        assertFalse(FeedAuthors.LoadingFollowed.leavesOut(FOLLOWED_UID))
    }

    @Test
    fun leavesOut_followedUnknown_leavesEveryoneOut() {
        assertTrue(FeedAuthors.UnknownFollowed.leavesOut(FOLLOWED_UID))
    }
}
