@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.animal

import edu.csuci.tweeter.data.AnimalVote
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeLikeRepository
import edu.csuci.tweeter.data.FakePhotoRepository
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.FakeVoteRepository
import edu.csuci.tweeter.data.PostDeleter
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.data.VoteChange
import edu.csuci.tweeter.ui.feed.PostLiker
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import edu.csuci.tweeter.ui.voting.PostVoter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val ISLAND_FOX = "Island fox"
private const val USER = "user"
private const val NEWER_FOX = "newer fox"
private const val OLDER_FOX = "older fox"
private const val POSSUM = "Possum"
private const val VOTE_ROUND = 0

class AnimalPostsViewModelTest {
    private val showOnMapRequest = ShowPostRequest()
    private val accountRepository = FakeAccountRepository(initialUid = USER)
    private val likeRepository = FakeLikeRepository(initialLikedPostIds = setOf(OLDER_FOX))
    private val postRepository = FakePostRepository(
        initialPosts = listOf(
            SAMPLE_POSTS[0].copy(id = NEWER_FOX, animalType = ISLAND_FOX, authorUid = USER),
            SAMPLE_POSTS[1].copy(id = "coyote", animalType = "Coyote"),
            SAMPLE_POSTS[2].copy(id = OLDER_FOX, animalType = "island fox")
        )
    )
    private val olderFoxVote = AnimalVote(POSSUM, VOTE_ROUND)
    private val voteRepository = FakeVoteRepository(initialVotes = mapOf(OLDER_FOX to olderFoxVote))

    @Before
    fun useTestMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_listsRowsOfPostsNamingAnimalNewestFirst() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        assertArrayEquals(intArrayOf(0, 2), viewModel.uiState.value.listedPosts.rows)
    }

    @Test
    fun uiState_postOfAnimalAdded_listsItFirst() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        postRepository.addPost(SAMPLE_POSTS[0].copy(id = "newest fox", animalType = ISLAND_FOX))

        val listedPosts = viewModel.uiState.value.listedPosts
        assertEquals("newest fox", listedPosts.table.id(listedPosts.rows.first()))
    }

    @Test
    fun showOnMap_asksMapToShowPost() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        viewModel.showOnMap(NEWER_FOX)

        assertEquals(NEWER_FOX, showOnMapRequest.postId.value)
    }

    @Test
    fun deletePost_usersOwnPost_dropsItFromTheList() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        viewModel.deletePost(NEWER_FOX)

        val listedPosts = viewModel.uiState.value.listedPosts
        assertEquals(listOf(OLDER_FOX), listedPosts.rows.map(listedPosts.table::id))
    }

    @Test
    fun uiState_holdsPostsUserLiked() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        assertEquals(setOf(OLDER_FOX), viewModel.uiState.value.likedPostIds)
    }

    @Test
    fun uiState_userLoggedIn_canLike() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        assertTrue(viewModel.uiState.value.canLike)
    }

    @Test
    fun uiState_guest_cannotLike() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        accountRepository.logOut()

        assertFalse(viewModel.uiState.value.canLike)
    }

    @Test
    fun toggleLike_postNotLiked_likesIt() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        viewModel.toggleLike(NEWER_FOX)

        assertEquals(setOf(OLDER_FOX, NEWER_FOX), viewModel.uiState.value.likedPostIds)
    }

    @Test
    fun toggleLike_likedPost_unlikesIt() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        viewModel.toggleLike(OLDER_FOX)

        assertEquals(emptySet<String>(), viewModel.uiState.value.likedPostIds)
    }

    @Test
    fun userVotes_holdsUsersVotes() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        assertEquals(mapOf(OLDER_FOX to olderFoxVote), viewModel.userVotes.value)
    }

    @Test
    fun changeVote_savesUsersVote() = runTest {
        val viewModel = collectedAnimalPostsViewModel()
        val change = VoteChange(OLDER_FOX, VOTE_ROUND, previousAnimal = POSSUM, newAnimal = null)

        viewModel.changeVote(change)

        assertEquals(emptyMap<String, AnimalVote>(), viewModel.userVotes.value)
    }

    @Test
    fun reclassify_reclassifiesPost() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        viewModel.reclassify(NEWER_FOX, POSSUM)

        assertEquals(mapOf(NEWER_FOX to POSSUM), voteRepository.reclassifiedAnimals.value)
    }

    private fun TestScope.collectedAnimalPostsViewModel(): AnimalPostsViewModel {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = AnimalPostsViewModel(
            animal = ISLAND_FOX,
            postRepository = postRepository,
            postDeleter = PostDeleter(
                postRepository = postRepository,
                photoRepository = FakePhotoRepository(),
                accountRepository = accountRepository,
                scope = CoroutineScope(dispatcher)
            ),
            accountRepository = accountRepository,
            postLiker = PostLiker(likeRepository),
            postVoter = PostVoter(voteRepository),
            showOnMapRequest = showOnMapRequest,
            rowsDispatcher = dispatcher,
            sharing = SharingStarted.WhileSubscribed()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.userVotes.collect()
        }
        return viewModel
    }
}
