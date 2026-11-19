@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.voting

import edu.csuci.tweeter.data.AnimalVote
import edu.csuci.tweeter.data.Avatar
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.FakeVoteRepository
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.data.VoteRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val POST_ID = "raccoon post"
private const val USER_UID = "maya"
private const val AUTHOR_UID = "jordan"
private const val AUTHOR_INITIALS = "JR"
private const val PHOTO_URL = "https://example.com/raccoon.jpg"
private const val RACCOON = "Raccoon"
private const val POSSUM = "Possum"
private const val ROUND = 2

class VotingViewModelTest {
    private val postRepository = FakePostRepository(
        initialPosts = listOf(
            SAMPLE_POSTS[0].copy(
                id = POST_ID,
                authorUid = AUTHOR_UID,
                avatar = Avatar(initials = AUTHOR_INITIALS),
                imageUri = PHOTO_URL,
                animalType = RACCOON,
                voteRound = ROUND
            )
        ) + SAMPLE_POSTS
    )

    @Before
    fun useTestMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_holdsPostsPhoto() = runTest {
        val viewModel = collectedVotingViewModel()

        assertEquals(PHOTO_URL, viewModel.uiState.value.imageUri)
    }

    @Test
    fun uiState_holdsPostersInitials() = runTest {
        val viewModel = collectedVotingViewModel()

        assertEquals(AUTHOR_INITIALS, viewModel.uiState.value.posterInitials)
    }

    @Test
    fun uiState_listsPostersAnimalFirstThenVotedAnimals() = runTest {
        val votes = FakeVoteRepository(initialVoteCounts = mapOf(POST_ID to mapOf(POSSUM to 2)))

        val viewModel = collectedVotingViewModel(votes)

        assertEquals(listOf(RACCOON, POSSUM), viewModel.uiState.value.shares.map { it.animal })
    }

    @Test
    fun uiState_voteOfPostsRound_isBallotsVote() = runTest {
        val votes = FakeVoteRepository(initialVotes = mapOf(POST_ID to AnimalVote(POSSUM, ROUND)))

        val viewModel = collectedVotingViewModel(votes)

        assertEquals(POSSUM, viewModel.uiState.value.ballot?.userVote)
    }

    @Test
    fun uiState_voteOfEarlierRound_isNotCounted() = runTest {
        val staleVote = AnimalVote(POSSUM, ROUND - 1)
        val votes = FakeVoteRepository(initialVotes = mapOf(POST_ID to staleVote))

        val viewModel = collectedVotingViewModel(votes)

        assertNull(viewModel.uiState.value.ballot?.userVote)
    }

    @Test
    fun uiState_postShown_isNotGone() = runTest {
        val viewModel = collectedVotingViewModel()

        assertFalse(viewModel.uiState.value.isPostGone)
    }

    @Test
    fun uiState_postDeleted_isGone() = runTest {
        val viewModel = collectedVotingViewModel()

        postRepository.deletePost(POST_ID)

        assertTrue(viewModel.uiState.value.isPostGone)
    }

    @Test
    fun uiState_guest_votesAsGuest() = runTest {
        val viewModel = collectedVotingViewModel(loggedInUid = null)

        assertEquals(Voter.GUEST, viewModel.uiState.value.voter)
    }

    @Test
    fun uiState_poster_votesAsPoster() = runTest {
        val viewModel = collectedVotingViewModel(loggedInUid = AUTHOR_UID)

        assertEquals(Voter.POSTER, viewModel.uiState.value.voter)
    }

    @Test
    fun uiState_otherUser_votesAsUser() = runTest {
        val viewModel = collectedVotingViewModel(loggedInUid = USER_UID)

        assertEquals(Voter.USER, viewModel.uiState.value.voter)
    }

    @Test
    fun toggleVote_noVote_votesForAnimal() = runTest {
        val viewModel = collectedVotingViewModel()

        viewModel.toggleVote(POSSUM)

        assertEquals(listOf(1, 1), viewModel.uiState.value.shares.map { it.voteCount })
        assertEquals(POSSUM, viewModel.uiState.value.ballot?.userVote)
    }

    @Test
    fun toggleVote_animalVotedFor_withdrawsVote() = runTest {
        val votes = FakeVoteRepository(
            initialVotes = mapOf(POST_ID to AnimalVote(POSSUM, ROUND)),
            initialVoteCounts = mapOf(POST_ID to mapOf(POSSUM to 1))
        )
        val viewModel = collectedVotingViewModel(votes)

        viewModel.toggleVote(POSSUM)

        assertEquals(listOf(RACCOON), viewModel.uiState.value.shares.map { it.animal })
        assertNull(viewModel.uiState.value.ballot?.userVote)
    }

    @Test
    fun toggleVote_otherAnimalVotedFor_movesVoteToAnimal() = runTest {
        val votes = FakeVoteRepository(
            initialVotes = mapOf(POST_ID to AnimalVote(POSSUM, ROUND)),
            initialVoteCounts = mapOf(POST_ID to mapOf(POSSUM to 1))
        )
        val viewModel = collectedVotingViewModel(votes)

        viewModel.toggleVote(RACCOON)

        assertEquals(listOf(2), viewModel.uiState.value.shares.map { it.voteCount })
        assertEquals(RACCOON, viewModel.uiState.value.ballot?.userVote)
    }

    // The state follows the repositories while the test collects it, as it does in the app while
    // the voting page collects it.
    private fun TestScope.collectedVotingViewModel(
        votes: VoteRepository = FakeVoteRepository(),
        loggedInUid: String? = USER_UID
    ): VotingViewModel {
        val viewModel = VotingViewModel(
            postId = POST_ID,
            postRepository = postRepository,
            postVoter = PostVoter(votes),
            accountRepository = FakeAccountRepository(initialUid = loggedInUid),
            statesDispatcher = UnconfinedTestDispatcher(testScheduler),
            sharing = SharingStarted.WhileSubscribed()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        return viewModel
    }
}
