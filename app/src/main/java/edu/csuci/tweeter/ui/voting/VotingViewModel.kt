package edu.csuci.tweeter.ui.voting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.AnimalVote
import edu.csuci.tweeter.data.PostBallot
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.countedVoteOn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What [VotingScreen] shows.
 *
 * @property isLoading Whether the post is still being read.
 * @property isPostGone Whether the post was deleted, so there are no votes to show.
 * @property imageUri The post's photo.
 * @property posterInitials The initials of the poster's avatar, which mark the poster's animal.
 * @property shares Every animal voted for on the post, the poster's first.
 * @property ballot The user's vote on the post, which a change to it is worked out from; null
 *     while the post is read, or once it's gone.
 * @property voter Who the user is to the post: guests and the poster can't vote.
 */
data class VotingUiState(
    val isLoading: Boolean = true,
    val isPostGone: Boolean = false,
    val imageUri: String = "",
    val posterInitials: String = "",
    val shares: List<AnimalShare> = emptyList(),
    val ballot: PostBallot? = null,
    val voter: Voter = Voter.GUEST
)

/** The values of the post on the voting page, from its row of the [PostTable]. */
private data class VotedPost(
    val imageUri: String,
    val posterInitials: String,
    val posterAnimal: String,
    val authorUid: String,
    val round: Int
)

/**
 * Holds the votes on one post's animal for [VotingScreen], and changes the user's vote.
 *
 * @param postId The post whose votes these are.
 * @param postRepository Where the post comes from.
 * @param postVoter The post's vote counts and the user's votes, and saves the votes
 *     [toggleVote] changes.
 * @param accountRepository Who is logged in: guests and the poster can't vote.
 * @param statesDispatcher Where the post is found among the posts and its votes are put in
 *     order: off the main thread, since finding it is a pass over every post.
 * @param sharing When [uiState] follows the post and its votes: while the screen collects it.
 */
class VotingViewModel(
    private val postId: String,
    postRepository: PostRepository,
    private val postVoter: PostVoter,
    accountRepository: AccountRepository,
    statesDispatcher: CoroutineDispatcher,
    sharing: SharingStarted
) : ViewModel() {
    // The post, found once per table, and not again while it stays the same.
    private val votedPost: Flow<VotedPost?> = postRepository.getPostTable()
        .map(::votedPostIn)
        .distinctUntilChanged()

    /** The post and its votes, updated as they or the user's votes change, or the user logs in. */
    val uiState: StateFlow<VotingUiState> = combine(
        votedPost,
        // The post's listener reports every change to it, such as a like, with the same counts.
        postVoter.voteCounts(postId).distinctUntilChanged(),
        postVoter.userVotes(),
        accountRepository.loggedInUid
    ) { post, voteCounts, userVotes, loggedInUid ->
        if (post == null) {
            VotingUiState(isLoading = false, isPostGone = true)
        } else {
            uiStateOf(post, voteCounts, userVotes, loggedInUid)
        }
    }
        .flowOn(statesDispatcher)
        .stateIn(viewModelScope, sharing, VotingUiState())

    /**
     * Votes for [animal] instead of the user's current vote, or takes the vote back if it already
     * names [animal], unless a change to the post's votes is still being saved.
     */
    fun toggleVote(animal: String) {
        val ballot = uiState.value.ballot ?: return
        viewModelScope.launch { postVoter.toggleVote(ballot, animal) }
    }

    private fun votedPostIn(posts: PostTable): VotedPost? {
        val row = posts.rowOf(postId) ?: return null
        return VotedPost(
            imageUri = posts.imageUri(row),
            posterInitials = posts.avatarInitials(row),
            posterAnimal = posts.animalType(row),
            authorUid = posts.authorUid(row),
            round = posts.voteRound(row)
        )
    }

    private fun uiStateOf(
        post: VotedPost,
        voteCounts: Map<String, Int>,
        userVotes: Map<String, AnimalVote>,
        loggedInUid: String?
    ): VotingUiState = VotingUiState(
        isLoading = false,
        imageUri = post.imageUri,
        posterInitials = post.posterInitials,
        shares = animalSharesOf(post.posterAnimal, voteCounts),
        ballot = PostBallot(postId, post.round, userVotes.countedVoteOn(postId, post.round)),
        voter = Voter.of(loggedInUid, post.authorUid)
    )

    companion object {
        /**
         * Creates a [VotingViewModel] that reads the app's shared posts and votes.
         *
         * @param postId The post whose votes it holds.
         */
        fun createFactory(postId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                VotingViewModel(
                    postId = postId,
                    postRepository = application.container.postRepository,
                    postVoter = PostVoter(application.container.voteRepository),
                    accountRepository = application.container.accountRepository,
                    statesDispatcher = Dispatchers.Default,
                    sharing = application.container.whileScreenCollects
                )
            }
        }
    }
}
