package edu.csuci.tweeter.ui.profile

import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {
    private val profile = UserProfile(
        uid = AUTHOR_UID,
        displayName = "Maya",
        bio = "Bird photos.",
        photoUrl = ""
    )
    private val accountRepository = FakeAccountRepository(VIEWER_UID)
    private val profileRepository = FakeProfileRepository(profile)
    private val postRepository = FakePostRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_otherUser_readsTheirProfileAndPosts() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value as UserProfileUiState.Loaded

        assertEquals(profile, state.profile)
        assertTrue(postRepository.wasRead)
        assertTrue(state.rows.isEmpty())
        assertFalse(state.isOwnProfile)
    }

    @Test
    fun load_ownUser_identifiesOwnProfile() = runTest {
        accountRepository.loggedInUid.value = AUTHOR_UID

        val viewModel = createViewModel()
        val state = viewModel.uiState.value as UserProfileUiState.Loaded

        assertTrue(state.isOwnProfile)
    }

    @Test
    fun load_loggedOut_stillShowsPublicProfile() = runTest {
        accountRepository.loggedInUid.value = null

        val viewModel = createViewModel()
        val state = viewModel.uiState.value as UserProfileUiState.Loaded

        assertEquals(profile, state.profile)
        assertFalse(state.isLoggedIn)
        assertFalse(state.isOwnProfile)
    }

    private fun TestScope.createViewModel(): UserProfileViewModel {
        val viewModel = UserProfileViewModel(
            userUid = AUTHOR_UID,
            accountRepository = accountRepository,
            profileRepository = profileRepository,
            postRepository = postRepository
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        return viewModel
    }

    private class FakeProfileRepository(private val profile: UserProfile) : ProfileRepository {
        override fun getProfile(uid: String): Flow<UserProfile> = flowOf(profile.copy(uid = uid))

        override fun getProfiles(): Flow<List<UserProfile>> = flowOf(listOf(profile))

        override suspend fun saveProfile(profile: UserProfile) = Unit
    }

    private class FakePostRepository : PostRepository {
        var wasRead: Boolean = false

        override fun getPostTable(): Flow<PostTable> {
            wasRead = true
            return flowOf(PostTable.EMPTY)
        }

        override suspend fun addPost(post: Post) = Unit
    }

    private class FakeAccountRepository(uid: String?) : AccountRepository {
        override val loggedInUid = MutableStateFlow(uid)

        override suspend fun logIn(email: String, password: String) = Unit

        override suspend fun createAccount(email: String, password: String) = Unit

        override suspend fun logOut() = Unit

        override suspend fun resetPassword(email: String) = Unit
    }

    private companion object {
        const val AUTHOR_UID = "author"
        const val VIEWER_UID = "viewer"
    }
}
