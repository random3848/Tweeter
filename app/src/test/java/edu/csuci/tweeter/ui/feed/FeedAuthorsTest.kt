package edu.csuci.tweeter.ui.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FRIEND_UID = "jordan"
private const val STRANGER_UID = "sam"

class FeedAuthorsTest {
    @Test
    fun leavesOut_everyone_leavesNoneOut() {
        assertFalse(FeedAuthors.Everyone.leavesOut(STRANGER_UID))
    }

    @Test
    fun leavesOut_friend_keepsThem() {
        assertFalse(FeedAuthors.Friends(setOf(FRIEND_UID)).leavesOut(FRIEND_UID))
    }

    @Test
    fun leavesOut_stranger_leavesThemOut() {
        assertTrue(FeedAuthors.Friends(setOf(FRIEND_UID)).leavesOut(STRANGER_UID))
    }

    @Test
    fun leavesOut_whileFriendsAreRead_leavesNoneOutYet() {
        assertFalse(FeedAuthors.LoadingFriends.leavesOut(FRIEND_UID))
    }

    @Test
    fun leavesOut_friendsUnknown_leavesEveryoneOut() {
        assertTrue(FeedAuthors.UnknownFriends.leavesOut(FRIEND_UID))
    }
}
