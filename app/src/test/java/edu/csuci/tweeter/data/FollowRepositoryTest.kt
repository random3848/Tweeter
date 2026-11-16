package edu.csuci.tweeter.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val USER = "user"
private const val FRIEND = "friend"
private const val FOLLOWED = "followed"
private const val FOLLOWER = "follower"

@OptIn(ExperimentalCoroutinesApi::class)
class FollowRepositoryTest {
    private val repository = FakeFollowRepository()

    @Test
    fun observeFriendUids_returnsOnlyUsersWhoFollowEachOther() = runTest {
        followEachOther(USER, FRIEND)
        repository.addFollow(followerUid = USER, followedUid = FOLLOWED)
        repository.addFollow(followerUid = FOLLOWER, followedUid = USER)

        assertEquals(setOf(FRIEND), repository.observeFriendUids(USER).first())
    }

    @Test
    fun observeFriendUids_friendUnfollows_dropsThem() = runTest {
        followEachOther(USER, FRIEND)
        val friendUids = mutableListOf<Set<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeFriendUids(USER).toList(friendUids)
        }

        repository.removeFollow(followerUid = FRIEND, followedUid = USER)

        assertEquals(emptySet<String>(), friendUids.last())
    }

    private fun followEachOther(oneUid: String, otherUid: String) {
        repository.addFollow(followerUid = oneUid, followedUid = otherUid)
        repository.addFollow(followerUid = otherUid, followedUid = oneUid)
    }
}
