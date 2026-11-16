package edu.csuci.tweeter.ui.profile

import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.FollowRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FollowViewModelTest {
    private val accountRepository = FakeAccountRepository(VIEWER_UID)
    private val followRepository = FakeFollowRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun follow_otherUser_createsRelationship() {
        val viewModel = createViewModel()

        viewModel.follow()

        assertEquals(OTHER_UID, followRepository.followedUid)
        assertTrue(viewModel.uiState.value.isFollowing)
    }

    @Test
    fun unfollow_followedUser_removesRelationship() {
        followRepository.following.value = true
        val viewModel = createViewModel()

        viewModel.unfollow()

        assertEquals(OTHER_UID, followRepository.unfollowedUid)
        assertFalse(viewModel.uiState.value.isFollowing)
    }

    @Test
    fun follow_ownProfile_doesNotCreateRelationship() {
        val viewModel = createViewModel(userUid = VIEWER_UID)

        viewModel.follow()

        assertFalse(viewModel.uiState.value.canFollow)
        assertEquals(null, followRepository.followedUid)
    }

    @Test
    fun follow_loggedOut_doesNotCreateRelationship() {
        accountRepository.loggedInUid.value = null
        val viewModel = createViewModel()

        viewModel.follow()

        assertFalse(viewModel.uiState.value.canFollow)
        assertEquals(null, followRepository.followedUid)
    }

    private fun createViewModel(userUid: String = OTHER_UID): FollowViewModel = FollowViewModel(
        userUid = userUid,
        accountRepository = accountRepository,
        followRepository = followRepository
    )

    private class FakeFollowRepository : FollowRepository {
        val following = MutableStateFlow(false)
        var followedUid: String? = null
        var unfollowedUid: String? = null

        override fun observeFollowing(followerUid: String, followedUid: String): Flow<Boolean> =
            following

        override suspend fun follow(userUid: String) {
            followedUid = userUid
            following.value = true
        }

        override suspend fun unfollow(userUid: String) {
            unfollowedUid = userUid
            following.value = false
        }
    }

    private class FakeAccountRepository(uid: String?) : AccountRepository {
        override val loggedInUid = MutableStateFlow(uid)

        override suspend fun logIn(email: String, password: String) = Unit

        override suspend fun createAccount(email: String, password: String) = Unit

        override suspend fun logOut() = Unit

        override suspend fun resetPassword(email: String) = Unit
    }

    private companion object {
        const val VIEWER_UID = "viewer"
        const val OTHER_UID = "other-user"
    }
}
