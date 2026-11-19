package edu.csuci.tweeter.ui.voting

import org.junit.Assert.assertEquals
import org.junit.Test

private const val USER_UID = "maya"
private const val AUTHOR_UID = "jordan"

class VoterTest {
    @Test
    fun of_nobodyLoggedIn_isGuest() {
        assertEquals(Voter.GUEST, Voter.of(loggedInUid = null, authorUid = AUTHOR_UID))
    }

    @Test
    fun of_authorLoggedIn_isPoster() {
        assertEquals(Voter.POSTER, Voter.of(loggedInUid = AUTHOR_UID, authorUid = AUTHOR_UID))
    }

    @Test
    fun of_otherUserLoggedIn_isUser() {
        assertEquals(Voter.USER, Voter.of(loggedInUid = USER_UID, authorUid = AUTHOR_UID))
    }

    @Test
    fun of_postSharedWhileLoggedOut_isUser() {
        assertEquals(Voter.USER, Voter.of(loggedInUid = USER_UID, authorUid = ""))
    }
}
