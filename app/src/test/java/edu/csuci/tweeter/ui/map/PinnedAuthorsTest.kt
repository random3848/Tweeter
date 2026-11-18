package edu.csuci.tweeter.ui.map

import org.junit.Assert.assertSame
import org.junit.Test

private const val USER = "user"
private const val OTHER_USER = "other user"
private const val FRIEND = "friend"

class PinnedAuthorsTest {
    @Test
    fun loadingFriends_replacingSameUsersFriends_keepsThemPinned() {
        val friends = PinnedAuthors.Friends(userUid = USER, uids = setOf(FRIEND))

        assertSame(friends, PinnedAuthors.LoadingFriends(userUid = USER).replacing(friends))
    }

    @Test
    fun loadingFriends_replacingOtherUsersFriends_pinsNoneOfThem() {
        val loading = PinnedAuthors.LoadingFriends(userUid = OTHER_USER)
        val friends = PinnedAuthors.Friends(userUid = USER, uids = setOf(FRIEND))

        assertSame(loading, loading.replacing(friends))
    }
}
