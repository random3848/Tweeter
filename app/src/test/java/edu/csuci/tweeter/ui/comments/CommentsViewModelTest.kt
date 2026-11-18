@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.comments

import edu.csuci.tweeter.data.Comment
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeCommentRepository
import edu.csuci.tweeter.data.FakeProfileRepository
import edu.csuci.tweeter.data.UserProfile
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val POST_ID = "post"
private const val USER_UID = "maya"
private const val USERNAME = "maya.k"
private const val COMMENT_TEXT = "Nice hawk!"

class CommentsViewModelTest {
    private val commentRepository = FakeCommentRepository()
    private val profileRepository = FakeProfileRepository(
        profiles = listOf(
            UserProfile(uid = USER_UID, displayName = USERNAME, bio = "", photoUrl = "")
        )
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
    fun uiState_guest_cantComment() = runTest {
        val viewModel = collectedCommentsViewModel(loggedInUid = null)

        assertFalse(viewModel.uiState.value.canComment)
    }

    @Test
    fun uiState_loggedIn_canComment() = runTest {
        val viewModel = collectedCommentsViewModel(loggedInUid = USER_UID)

        assertTrue(viewModel.uiState.value.canComment)
    }

    @Test
    fun uiState_commentsCantBeRead_failsButLoggedInUserCanStillComment() = runTest {
        commentRepository.failsToRead = true

        val viewModel = collectedCommentsViewModel(loggedInUid = USER_UID)

        assertTrue(viewModel.uiState.value.hasFailed)
        assertTrue(viewModel.uiState.value.canComment)
    }

    @Test
    fun sendComment_addsTrimmedCommentUnderUsersName() = runTest {
        val viewModel = collectedCommentsViewModel(loggedInUid = USER_UID)
        viewModel.changeDraft("  $COMMENT_TEXT  ")

        viewModel.sendComment()

        val comment = viewModel.uiState.value.comments.single()
        assertEquals(COMMENT_TEXT, comment.text)
        assertEquals(USERNAME, comment.username)
    }

    @Test
    fun sendComment_clearsDraft() = runTest {
        val viewModel = collectedCommentsViewModel(loggedInUid = USER_UID)
        viewModel.changeDraft(COMMENT_TEXT)

        viewModel.sendComment()

        assertEquals("", viewModel.draft)
    }

    @Test
    fun sendComment_blankDraft_addsNothing() = runTest {
        val viewModel = collectedCommentsViewModel(loggedInUid = USER_UID)
        viewModel.changeDraft("   ")

        viewModel.sendComment()

        assertTrue(viewModel.uiState.value.comments.isEmpty())
    }

    @Test
    fun sendComment_rejected_keepsDraft() = runTest {
        val viewModel = collectedCommentsViewModel(loggedInUid = USER_UID)
        commentRepository.failsToAdd = true
        viewModel.changeDraft(COMMENT_TEXT)

        viewModel.sendComment()

        assertEquals(COMMENT_TEXT, viewModel.draft)
        assertFalse(viewModel.isSending)
    }

    @Test
    fun changeDraft_tooLong_cutsToMaxLength() {
        val viewModel = commentsViewModel(loggedInUid = USER_UID)

        viewModel.changeDraft("a".repeat(Comment.MAX_LENGTH + 1))

        assertEquals(Comment.MAX_LENGTH, viewModel.draft.length)
    }

    private fun commentsViewModel(loggedInUid: String?): CommentsViewModel = CommentsViewModel(
        postId = POST_ID,
        commentRepository = commentRepository,
        profileRepository = profileRepository,
        accountRepository = FakeAccountRepository(initialUid = loggedInUid),
        sharing = SharingStarted.WhileSubscribed()
    )

    // The state follows the repository while the test collects it, as it does while the screen
    // collects it in the app.
    private fun TestScope.collectedCommentsViewModel(loggedInUid: String?): CommentsViewModel {
        val viewModel = commentsViewModel(loggedInUid)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        return viewModel
    }
}
