package edu.csuci.tweeter.ui.profile

import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.FollowException
import edu.csuci.tweeter.data.FollowRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
        assertEquals(FollowUiState.Loaded(isFollowing = true), viewModel.uiState.value)
    }

    @Test
    fun unfollow_followedUser_removesRelationship() {
        followRepository.following.value = true
        val viewModel = createViewModel()

        viewModel.unfollow()

        assertEquals(OTHER_UID, followRepository.unfollowedUid)
        assertEquals(FollowUiState.Loaded(isFollowing = false), viewModel.uiState.value)
    }

    @Test
    fun follow_ownProfile_doesNotCreateRelationship() {
        val viewModel = createViewModel(userUid = VIEWER_UID)

        viewModel.follow()

        assertEquals(FollowUiState.NotAllowed, viewModel.uiState.value)
        assertEquals(null, followRepository.followedUid)
    }

    @Test
    fun follow_loggedOut_doesNotCreateRelationship() {
        accountRepository.loggedInUid.value = null
        val viewModel = createViewModel()

        viewModel.follow()

        assertEquals(FollowUiState.NotAllowed, viewModel.uiState.value)
        assertEquals(null, followRepository.followedUid)
    }

    @Test
    fun observeFollowing_failure_showsErrorAndBlocksChanges() {
        followRepository.readFails.value = true
        val viewModel = createViewModel()

        assertEquals(FollowUiState.ReadFailed, viewModel.uiState.value)

        viewModel.follow()

        assertEquals(null, followRepository.followedUid)
    }

    @Test
    fun retry_afterReadFailure_loadsRelationshipAndKeepsListening() {
        followRepository.readFails.value = true
        val viewModel = createViewModel()

        assertEquals(FollowUiState.ReadFailed, viewModel.uiState.value)

        followRepository.readFails.value = false
        followRepository.following.value = true
        viewModel.retry()

        assertEquals(2, followRepository.observeCount)
        assertEquals(FollowUiState.Loaded(isFollowing = true), viewModel.uiState.value)

        followRepository.following.value = false

        assertEquals(FollowUiState.Loaded(isFollowing = false), viewModel.uiState.value)
    }

    @Test
    fun follow_whileChanging_blocksChangesAndIgnoresSecondTap() {
        followRepository.holdChanges()
        val viewModel = createViewModel()
        viewModel.follow()

        viewModel.follow()
        viewModel.unfollow()

        assertEquals(1, followRepository.changeCount)
        assertEquals(
            FollowUiState.Loaded(isFollowing = false, change = FollowChange.RUNNING),
            viewModel.uiState.value
        )
    }

    @Test
    fun follow_changeFinishes_allowsChangesAgain() {
        followRepository.holdChanges()
        val viewModel = createViewModel()
        viewModel.follow()

        followRepository.finishChanges()

        assertEquals(FollowUiState.Loaded(isFollowing = true), viewModel.uiState.value)
    }

    @Test
    fun follow_failure_showsErrorAndAllowsTryingAgain() {
        followRepository.changeFails = true
        val viewModel = createViewModel()

        viewModel.follow()

        assertEquals(
            FollowUiState.Loaded(isFollowing = false, change = FollowChange.FAILED),
            viewModel.uiState.value
        )
    }

    @Test
    fun unfollow_failure_showsErrorAndKeepsFollowing() {
        followRepository.following.value = true
        followRepository.changeFails = true
        val viewModel = createViewModel()

        viewModel.unfollow()

        assertEquals(
            FollowUiState.Loaded(isFollowing = true, change = FollowChange.FAILED),
            viewModel.uiState.value
        )
    }

    @Test
    fun retry_afterChangeFailure_doesNotReadAgain() {
        followRepository.changeFails = true
        val viewModel = createViewModel()
        viewModel.follow()

        viewModel.retry()

        assertEquals(1, followRepository.observeCount)
        assertEquals(
            FollowUiState.Loaded(isFollowing = false, change = FollowChange.FAILED),
            viewModel.uiState.value
        )
    }

    @Test
    fun follow_afterChangeFailure_triesAgainAndClearsError() {
        followRepository.changeFails = true
        val viewModel = createViewModel()
        viewModel.follow()
        followRepository.changeFails = false

        viewModel.follow()

        assertEquals(2, followRepository.changeCount)
        assertEquals(FollowUiState.Loaded(isFollowing = true), viewModel.uiState.value)
    }

    @Test
    fun observeFollowing_failureWhileChanging_showsRetryAndBlocksChanges() {
        followRepository.holdChanges()
        val viewModel = createViewModel()
        viewModel.follow()

        followRepository.readFails.value = true
        viewModel.follow()

        assertEquals(1, followRepository.changeCount)
        assertEquals(FollowUiState.ReadFailed, viewModel.uiState.value)
    }

    @Test
    fun observeFollowing_failureWhileChanging_keepsShowingRetryAfterChangeFinishes() {
        followRepository.holdChanges()
        val viewModel = createViewModel()
        viewModel.follow()
        followRepository.readFails.value = true

        followRepository.finishChanges()

        assertEquals(OTHER_UID, followRepository.followedUid)
        assertEquals(FollowUiState.ReadFailed, viewModel.uiState.value)
    }

    @Test
    fun retry_afterReadFailureWhileChanging_loadsChangedRelationship() {
        followRepository.holdChanges()
        val viewModel = createViewModel()
        viewModel.follow()
        followRepository.readFails.value = true
        followRepository.finishChanges()
        followRepository.readFails.value = false

        viewModel.retry()

        assertEquals(2, followRepository.observeCount)
        assertEquals(FollowUiState.Loaded(isFollowing = true), viewModel.uiState.value)
    }

    private fun createViewModel(userUid: String = OTHER_UID): FollowViewModel = FollowViewModel(
        userUid = userUid,
        accountRepository = accountRepository,
        followRepository = followRepository
    )

    private class FakeFollowRepository : FollowRepository {
        val following = MutableStateFlow(false)
        val readFails = MutableStateFlow(false)
        var changeFails: Boolean = false
        var followedUid: String? = null
        var unfollowedUid: String? = null
        var observeCount: Int = 0
        var changeCount: Int = 0
        private var changesAnswered = CompletableDeferred(Unit)

        // Follows and unfollows wait until finishChanges(), like requests the server hasn't
        // answered yet.
        fun holdChanges() {
            changesAnswered = CompletableDeferred()
        }

        fun finishChanges() {
            changesAnswered.complete(Unit)
        }

        override fun observeFollowing(followerUid: String, followedUid: String): Flow<Boolean> =
            flow {
                observeCount++
                emitAll(
                    combine(following, readFails) { isFollowing, fails ->
                        if (fails) {
                            throw FollowException(IllegalStateException("Could not read following"))
                        }
                        isFollowing
                    }
                )
            }

        override fun observeFollowerCount(userUid: String): Flow<Int> =
            kotlinx.coroutines.flow.flowOf(0)

        override fun observeFollowingCount(userUid: String): Flow<Int> =
            kotlinx.coroutines.flow.flowOf(0)

        override fun observeFollowerUids(followedUid: String): Flow<Set<String>> =
            error("Not used by FollowViewModel")

        override suspend fun follow(userUid: String) {
            answerChange()
            followedUid = userUid
            following.value = true
        }

        override suspend fun unfollow(userUid: String) {
            answerChange()
            unfollowedUid = userUid
            following.value = false
        }

        private suspend fun answerChange() {
            changeCount++
            changesAnswered.await()
            if (changeFails) {
                throw FollowException(IllegalStateException("Could not change following"))
            }
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
