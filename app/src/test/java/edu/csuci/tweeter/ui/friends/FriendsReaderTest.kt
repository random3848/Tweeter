@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.friends

import edu.csuci.tweeter.data.FakeFollowRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val MAYA = "maya"
private const val JORDAN = "jordan"
private const val SAM = "sam"

class FriendsReaderTest {
    // Maya and Jordan follow each other; Maya follows Sam, who doesn't follow back.
    private val follows = FakeFollowRepository().apply {
        addFollow(MAYA, JORDAN)
        addFollow(JORDAN, MAYA)
        addFollow(MAYA, SAM)
    }
    private val reader = FriendsReader(follows)

    @Test
    fun friendsOf_read_areTheMutualFollows() = runTest {
        assertEquals(UserFriends.Known(setOf(JORDAN)), reader.friendsOf(MAYA).knownFriends())
    }

    @Test
    fun friendsOf_whileReading_isLoading() = runTest {
        follows.readsAnswered = CompletableDeferred()

        assertEquals(UserFriends.Loading, reader.friendsOf(MAYA).first())
    }

    @Test
    fun friendsOf_readFails_isUnknown() = runTest {
        follows.failsReads = true

        assertEquals(
            listOf(UserFriends.Loading, UserFriends.Unknown),
            reader.friendsOf(MAYA).take(2).toList()
        )
    }

    @Test
    fun retry_afterReadFailed_readsThemAgain() = runTest {
        follows.failsReads = true
        val friends = mutableListOf<UserFriends>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            reader.friendsOf(MAYA).collect(friends::add)
        }

        follows.failsReads = false
        reader.retry()

        assertEquals(UserFriends.Known(setOf(JORDAN)), friends.last())
    }

    @Test
    fun friendsOf_sameUserReadAgain_startsWithTheFriendsReadLast() = runTest {
        reader.friendsOf(MAYA).knownFriends()
        follows.readsAnswered = CompletableDeferred()

        assertEquals(UserFriends.Known(setOf(JORDAN)), reader.friendsOf(MAYA).first())
    }

    @Test
    fun friendsOf_otherUserAfterFirst_isLoadingUntilRead() = runTest {
        reader.friendsOf(MAYA).knownFriends()
        follows.readsAnswered = CompletableDeferred()

        assertEquals(UserFriends.Loading, reader.friendsOf(JORDAN).first())
    }

    private suspend fun Flow<UserFriends>.knownFriends(): UserFriends =
        first { it is UserFriends.Known }
}
