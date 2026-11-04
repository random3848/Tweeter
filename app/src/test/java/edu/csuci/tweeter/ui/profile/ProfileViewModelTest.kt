@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.profile

import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.FakeProfileRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.data.UserProfile
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val USER_UID = "maya"
private const val OTHER_UID = "jordan"
private const val ONCE = 1

class ProfileViewModelTest {
    private val profile = UserProfile(
        uid = USER_UID,
        displayName = "Maya",
        bio = "Spiders, mostly.",
        photoUrl = ""
    )
    private val newer = SAMPLE_POSTS.first().copy(id = "newer", authorUid = USER_UID)
    private val other = SAMPLE_POSTS.first().copy(id = "other", authorUid = OTHER_UID)
    private val older = SAMPLE_POSTS.first().copy(id = "older", authorUid = USER_UID)
    private val posts = listOf(newer, other, older)
    private val postRepository = FakePostRepository(initialPosts = posts)

    @Before
    fun useTestMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_nobodyLoggedIn_isLoggedOut() = runTest {
        val viewModel = profileViewModel(FakeAccountRepository(initialUid = null))

        val state = viewModel.uiState.value

        assertEquals(ProfileUiState.LoggedOut, state)
    }

    @Test
    fun uiState_loggedIn_showsProfileAndOnlyTheirRowsInTableOrder() = runTest {
        val viewModel = profileViewModel(FakeAccountRepository(initialUid = USER_UID))

        val state = viewModel.uiState.value

        val expected = ProfileUiState.LoggedIn(
            profile = profile,
            table = PostTable.of(posts),
            rows = intArrayOf(posts.indexOf(newer), posts.indexOf(older))
        )
        assertEquals(expected, state)
    }

    @Test
    fun uiState_userAddsPost_showsItFirst() = runTest {
        val viewModel = profileViewModel(FakeAccountRepository(initialUid = USER_UID))
        val post = newer.copy(id = "new", timestamp = Instant.now())

        postRepository.addPost(post)

        val state = viewModel.uiState.value as ProfileUiState.LoggedIn
        assertEquals(post.id, state.table.id(state.rows.first()))
    }

    @Test
    fun logOut_asksAccountRepositoryAndShowsLoggedOut() = runTest {
        val accountRepository = FakeAccountRepository(initialUid = USER_UID)
        val viewModel = profileViewModel(accountRepository)

        viewModel.logOut()

        assertEquals(ONCE, accountRepository.logOutCount)
        assertEquals(ProfileUiState.LoggedOut, viewModel.uiState.value)
    }

    // Profile follows its sources only while something collects its state, as ProfileScreen
    // does.
    private fun TestScope.profileViewModel(
        accountRepository: FakeAccountRepository
    ): ProfileViewModel {
        val viewModel = ProfileViewModel(
            accountRepository = accountRepository,
            profileRepository = FakeProfileRepository(profiles = listOf(profile)),
            postRepository = postRepository
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        return viewModel
    }
}
