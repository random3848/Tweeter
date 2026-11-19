@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.saved

import edu.csuci.tweeter.data.AnimalVote
import edu.csuci.tweeter.data.BookmarkRepository
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeBookmarkRepository
import edu.csuci.tweeter.data.FakeLikeRepository
import edu.csuci.tweeter.data.FakePhotoRepository
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.FakeVoteRepository
import edu.csuci.tweeter.data.ListenerBookmarkRepository
import edu.csuci.tweeter.data.PostBallot
import edu.csuci.tweeter.data.PostDeleter
import edu.csuci.tweeter.data.PostRows
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.ui.feed.PostBookmarker
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val USER = "user"
private const val NEWER_SAVED = "newer saved"
private const val NOT_SAVED = "not saved"
private const val OLDER_SAVED = "older saved"
private const val POSSUM = "Possum"
private const val VOTE_ROUND = 0

class SavedPostsViewModelTest {
    private val showOnMapRequest = ShowPostRequest()
    private val accountRepository = FakeAccountRepository(initialUid = USER)
    private val likeRepository = FakeLikeRepository(initialLikedPostIds = setOf(OLDER_SAVED))
    private val bookmarkRepository =
        FakeBookmarkRepository(initialBookmarkedPostIds = setOf(OLDER_SAVED, NEWER_SAVED))
    private val postRepository = FakePostRepository(
        initialPosts = listOf(
            SAMPLE_POSTS[0].copy(id = NEWER_SAVED, authorUid = USER),
            SAMPLE_POSTS[1].copy(id = NOT_SAVED),
            SAMPLE_POSTS[2].copy(id = OLDER_SAVED)
        )
    )
    private val olderSavedVote = AnimalVote(POSSUM, VOTE_ROUND)
    private val voteRepository =
        FakeVoteRepository(initialVotes = mapOf(OLDER_SAVED to olderSavedVote))

    @Before
    fun useTestMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_listsSavedPostsNewestFirst() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        assertEquals(listOf(NEWER_SAVED, OLDER_SAVED), viewModel.uiState.value.listedPosts.ids())
    }

    @Test
    fun uiState_listed_isNotLoading() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun uiState_holdsPostsUserSaved() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        assertEquals(setOf(NEWER_SAVED, OLDER_SAVED), viewModel.uiState.value.bookmarkedPostIds)
    }

    @Test
    fun uiState_savedPostDeleted_leavesItOut() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        postRepository.deletePost(OLDER_SAVED)

        assertEquals(listOf(NEWER_SAVED), viewModel.uiState.value.listedPosts.ids())
    }

    @Test
    fun uiState_everyPostUnsaved_listsNone() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        bookmarkRepository.unbookmark(NEWER_SAVED)
        bookmarkRepository.unbookmark(OLDER_SAVED)

        assertEquals(emptyList<String>(), viewModel.uiState.value.listedPosts.ids())
    }

    @Test
    fun uiState_sameBookmarksAgain_keepsState() = runTest {
        val bookmarks = ListenerBookmarkRepository()
        bookmarks.deliver(setOf(OLDER_SAVED))
        val viewModel = collectedSavedPostsViewModel(bookmarks)
        val uiState = viewModel.uiState.value

        bookmarks.deliver(setOf(OLDER_SAVED))

        assertSame(uiState, viewModel.uiState.value)
    }

    @Test
    fun toggleBookmark_savedPost_dropsItFromTheList() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        viewModel.toggleBookmark(NEWER_SAVED)

        assertEquals(listOf(OLDER_SAVED), viewModel.uiState.value.listedPosts.ids())
    }

    @Test
    fun toggleBookmark_postNotSaved_savesIt() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        viewModel.toggleBookmark(NOT_SAVED)

        assertEquals(
            listOf(NEWER_SAVED, NOT_SAVED, OLDER_SAVED),
            viewModel.uiState.value.listedPosts.ids()
        )
    }

    @Test
    fun uiState_holdsPostsUserLiked() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        assertEquals(setOf(OLDER_SAVED), viewModel.uiState.value.likedPostIds)
    }

    @Test
    fun toggleLike_postNotLiked_likesIt() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        viewModel.toggleLike(NEWER_SAVED)

        assertEquals(setOf(OLDER_SAVED, NEWER_SAVED), viewModel.uiState.value.likedPostIds)
    }

    @Test
    fun userVotes_holdsUsersVotes() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        assertEquals(mapOf(OLDER_SAVED to olderSavedVote), viewModel.userVotes.value)
    }

    @Test
    fun toggleVote_animalVotedFor_withdrawsUsersVote() = runTest {
        val viewModel = collectedSavedPostsViewModel()
        val ballot = PostBallot(OLDER_SAVED, VOTE_ROUND, userVote = POSSUM)

        viewModel.toggleVote(ballot, POSSUM)

        assertEquals(emptyMap<String, AnimalVote>(), viewModel.userVotes.value)
    }

    @Test
    fun reclassify_reclassifiesPost() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        viewModel.reclassify(NEWER_SAVED, POSSUM)

        assertEquals(mapOf(NEWER_SAVED to POSSUM), voteRepository.reclassifiedAnimals.value)
    }

    @Test
    fun uiState_userLoggedIn_canLike() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        assertTrue(viewModel.uiState.value.canLike)
    }

    @Test
    fun uiState_guest_cannotLike() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        accountRepository.logOut()

        assertFalse(viewModel.uiState.value.canLike)
    }

    @Test
    fun showOnMap_asksMapToShowPost() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        viewModel.showOnMap(OLDER_SAVED)

        assertEquals(OLDER_SAVED, showOnMapRequest.postId.value)
    }

    @Test
    fun deletePost_usersOwnPost_dropsItFromTheList() = runTest {
        val viewModel = collectedSavedPostsViewModel()

        viewModel.deletePost(NEWER_SAVED)

        assertEquals(listOf(OLDER_SAVED), viewModel.uiState.value.listedPosts.ids())
    }

    private fun PostRows.ids(): List<String> = rows.map(table::id)

    private fun TestScope.collectedSavedPostsViewModel(
        bookmarks: BookmarkRepository = bookmarkRepository
    ): SavedPostsViewModel {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = SavedPostsViewModel(
            postRepository = postRepository,
            postBookmarker = PostBookmarker(bookmarks),
            postLiker = PostLiker(likeRepository),
            postVoter = PostVoter(voteRepository),
            postDeleter = PostDeleter(
                postRepository = postRepository,
                photoRepository = FakePhotoRepository(),
                accountRepository = accountRepository,
                scope = CoroutineScope(dispatcher)
            ),
            accountRepository = accountRepository,
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
