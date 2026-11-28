@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.following

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
private const val ALEX = "alex"

class FollowedUsersReaderTest {
    // Maya follows Jordan, who follows her back, and Sam, who doesn't; Alex follows Maya.
    private val follows = FakeFollowRepository().apply {
        addFollow(MAYA, JORDAN)
        addFollow(JORDAN, MAYA)
        addFollow(MAYA, SAM)
        addFollow(ALEX, MAYA)
    }
    private val reader = FollowedUsersReader(follows)

    @Test
    fun followedBy_read_areEveryoneTheUserFollows() = runTest {
        assertEquals(
            FollowedUsers.Known(setOf(JORDAN, SAM)),
            reader.followedBy(MAYA).knownFollowedUsers()
        )
    }

    @Test
    fun followedBy_userFollowsSomeoneNew_addsThem() = runTest {
        val followedUsers = mutableListOf<FollowedUsers>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            reader.followedBy(MAYA).collect(followedUsers::add)
        }

        follows.addFollow(MAYA, ALEX)

        assertEquals(FollowedUsers.Known(setOf(JORDAN, SAM, ALEX)), followedUsers.last())
    }

    @Test
    fun followedBy_userUnfollows_dropsThem() = runTest {
        val followedUsers = mutableListOf<FollowedUsers>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            reader.followedBy(MAYA).collect(followedUsers::add)
        }

        follows.removeFollow(MAYA, SAM)

        assertEquals(FollowedUsers.Known(setOf(JORDAN)), followedUsers.last())
    }

    @Test
    fun followedBy_whileReading_isLoading() = runTest {
        follows.readsAnswered = CompletableDeferred()

        assertEquals(FollowedUsers.Loading, reader.followedBy(MAYA).first())
    }

    @Test
    fun followedBy_readFails_isUnknown() = runTest {
        follows.failsReads = true

        assertEquals(
            listOf(FollowedUsers.Loading, FollowedUsers.Unknown),
            reader.followedBy(MAYA).take(2).toList()
        )
    }

    @Test
    fun retry_afterReadFailed_readsThemAgain() = runTest {
        follows.failsReads = true
        val followedUsers = mutableListOf<FollowedUsers>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            reader.followedBy(MAYA).collect(followedUsers::add)
        }

        follows.failsReads = false
        reader.retry()

        assertEquals(FollowedUsers.Known(setOf(JORDAN, SAM)), followedUsers.last())
    }

    @Test
    fun followedBy_sameUserReadAgain_startsWithTheUsersReadLast() = runTest {
        reader.followedBy(MAYA).knownFollowedUsers()
        follows.readsAnswered = CompletableDeferred()

        assertEquals(FollowedUsers.Known(setOf(JORDAN, SAM)), reader.followedBy(MAYA).first())
    }

    @Test
    fun followedBy_otherUserAfterFirst_isLoadingUntilRead() = runTest {
        reader.followedBy(MAYA).knownFollowedUsers()
        follows.readsAnswered = CompletableDeferred()

        assertEquals(FollowedUsers.Loading, reader.followedBy(JORDAN).first())
    }

    private suspend fun Flow<FollowedUsers>.knownFollowedUsers(): FollowedUsers =
        first { it is FollowedUsers.Known }
}
