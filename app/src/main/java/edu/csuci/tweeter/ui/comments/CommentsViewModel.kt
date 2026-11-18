package edu.csuci.tweeter.ui.comments

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.Comment
import edu.csuci.tweeter.data.CommentException
import edu.csuci.tweeter.data.CommentRepository
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.getLoggedInProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "CommentsViewModel"

/**
 * What a post's comments show when opened under it.
 *
 * @property comments The post's comments, oldest first.
 * @property isLoading Whether the first comments are still loading.
 * @property canComment Whether the user is logged in; guests can only read.
 * @property hasFailed Whether the comments couldn't be read. Logged-in users can still comment.
 */
data class CommentsUiState(
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = true,
    val canComment: Boolean = false,
    val hasFailed: Boolean = false
)

/**
 * Holds one post's comments, and sends the logged-in user's new ones.
 *
 * @param postId The post whose comments these are.
 * @param commentRepository Where comments are read and added.
 * @param profileRepository Supplies the commenter's name.
 * @param accountRepository Tells who is logged in.
 * @param sharing When [uiState] follows the comments.
 */
class CommentsViewModel(
    private val postId: String,
    private val commentRepository: CommentRepository,
    private val profileRepository: ProfileRepository,
    private val accountRepository: AccountRepository,
    sharing: SharingStarted
) : ViewModel() {
    /**
     * The comments and whether the user may add one. Comments that can't be read don't stop a
     * logged-in user from commenting.
     */
    val uiState: StateFlow<CommentsUiState> =
        combine(commentsOrNull(), accountRepository.loggedInUid) { comments, uid ->
            CommentsUiState(
                comments = comments.orEmpty(),
                isLoading = false,
                canComment = uid != null,
                hasFailed = comments == null
            )
        }
            .stateIn(viewModelScope, sharing, CommentsUiState())

    /** The comment being typed. */
    var draft by mutableStateOf("")
        private set

    /** Whether a comment is being sent, which disables Send. */
    var isSending by mutableStateOf(false)
        private set

    /** Replaces the comment being typed with [text], cut to [Comment.MAX_LENGTH]. */
    fun changeDraft(text: String) {
        draft = text.take(Comment.MAX_LENGTH)
    }

    /** Sends the draft as the logged-in user's comment, then clears it. Ignores a blank one. */
    fun sendComment() {
        val text = draft.trim()
        if (text.isEmpty() || isSending) {
            return
        }
        isSending = true
        viewModelScope.launch { sendOrLogFailure(text) }
    }

    // The post's comments, or null once they can't be read. Only the list shows the failure, so
    // the rest of the state, such as whether the user can comment, keeps following the account.
    private fun commentsOrNull(): Flow<List<Comment>?> = commentRepository.getComments(postId)
        .map<List<Comment>, List<Comment>?> { comments -> comments }
        .catch { error ->
            Log.e(TAG, "Could not read comments", error)
            emit(null)
        }

    private suspend fun sendOrLogFailure(text: String) {
        try {
            sendAsLoggedInUser(text)
        } catch (error: CommentException) {
            Log.e(TAG, "Could not send the comment", error)
        } finally {
            isSending = false
        }
    }

    private suspend fun sendAsLoggedInUser(text: String) {
        val author = profileRepository.getLoggedInProfile(accountRepository).first() ?: return
        commentRepository.addComment(postId, author, text)
        draft = ""
    }

    companion object {
        /** Creates a [CommentsViewModel] for the post with [postId]. */
        fun createFactory(postId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                CommentsViewModel(
                    postId = postId,
                    commentRepository = application.container.commentRepository,
                    profileRepository = application.container.profileRepository,
                    accountRepository = application.container.accountRepository,
                    sharing = application.container.whileScreenCollects
                )
            }
        }
    }
}
