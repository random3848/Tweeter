@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.report

import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeReportRepository
import edu.csuci.tweeter.data.Report
import edu.csuci.tweeter.data.ReportReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val REPORTER_UID = "maya"
private const val REPORTED_UID = "troll"
private const val POST_ID = "post"
private const val OTHER_USER_UID = "sam"
private const val DETAILS = "Kicked a cat"

class ReportViewModelTest {
    private val reportRepository = FakeReportRepository()
    private val accountRepository = FakeAccountRepository(REPORTER_UID)
    private val savedStateHandle = SavedStateHandle()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun send_postReport_sendsOneReportAndConfirms() {
        val viewModel = createViewModel(postId = POST_ID)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.changeDetails("  Selling stuff  ")

        viewModel.send()

        val expected = Report(
            reporterUid = REPORTER_UID,
            reportedUid = REPORTED_UID,
            postId = POST_ID,
            reason = ReportReason.SPAM,
            details = "Selling stuff"
        )
        assertEquals(listOf(expected), reportRepository.reports)
        assertEquals(ReportStatus.SENT, viewModel.status)
    }

    @Test
    fun send_profileReport_hasEmptyPostId() {
        val viewModel = createViewModel(postId = Report.NO_POST)
        viewModel.chooseReason(ReportReason.HARASSMENT)

        viewModel.send()

        assertEquals("", reportRepository.reports.single().postId)
        assertEquals(REPORTED_UID, reportRepository.reports.single().reportedUid)
    }

    @Test
    fun send_withoutReason_sendsNothing() {
        val viewModel = createViewModel()

        viewModel.send()

        assertTrue(reportRepository.reports.isEmpty())
        assertEquals(ReportStatus.EDITING, viewModel.status)
    }

    @Test
    fun send_failure_showsFailedAndRetrySends() {
        reportRepository.failsToSend = true
        val viewModel = createViewModel()
        viewModel.chooseReason(ReportReason.OTHER)

        viewModel.send()

        assertEquals(ReportStatus.FAILED, viewModel.status)
        reportRepository.failsToSend = false
        viewModel.send()
        assertEquals(1, reportRepository.reports.size)
        assertEquals(ReportStatus.SENT, viewModel.status)
    }

    @Test
    fun send_guest_sendsNothing() {
        val viewModel = createViewModel(accountRepository = FakeAccountRepository())
        viewModel.chooseReason(ReportReason.SPAM)

        viewModel.send()

        assertTrue(reportRepository.reports.isEmpty())
    }

    @Test
    fun send_ownContent_sendsNothing() {
        val viewModel = createViewModel(reportedUid = REPORTER_UID)
        viewModel.chooseReason(ReportReason.SPAM)

        viewModel.send()

        assertTrue(reportRepository.reports.isEmpty())
    }

    @Test
    fun changeDetails_tooLong_isCut() {
        val viewModel = createViewModel()

        viewModel.changeDetails("a".repeat(Report.MAX_DETAILS_LENGTH + 10))

        assertEquals(Report.MAX_DETAILS_LENGTH, viewModel.details.length)
    }

    @Test
    fun send_twiceWhileSending_sendsOneReport() {
        val sendCompletion = CompletableDeferred<Unit>()
        reportRepository.sendCompletion = sendCompletion
        val viewModel = createViewModel()
        viewModel.chooseReason(ReportReason.SPAM)

        viewModel.send()
        viewModel.send()
        sendCompletion.complete(Unit)

        assertEquals(1, reportRepository.reports.size)
    }

    @Test
    fun discardDraft_afterSent_opensEmptyForm() {
        val viewModel = createViewModel()
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.changeDetails(DETAILS)
        viewModel.send()

        viewModel.discardDraft()

        assertEquals(EMPTY_DRAFT, draftOf(viewModel))
    }

    @Test
    fun discardDraft_unsentDraft_opensEmptyForm() {
        val viewModel = createViewModel()
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.changeDetails(DETAILS)

        viewModel.discardDraft()

        assertEquals(EMPTY_DRAFT, draftOf(viewModel))
    }

    @Test
    fun discardDraft_whileSending_keepsSendingAndConfirmsLater() {
        val sendCompletion = CompletableDeferred<Unit>()
        reportRepository.sendCompletion = sendCompletion
        val viewModel = createViewModel()
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()

        viewModel.discardDraft()
        sendCompletion.complete(Unit)

        assertEquals(ReportStatus.SENT, viewModel.status)
    }

    @Test
    fun loggedInUserChange_clearsDraft() {
        val viewModel = createViewModel()
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.changeDetails(DETAILS)

        accountRepository.logInAs(OTHER_USER_UID)

        assertEquals(EMPTY_DRAFT, draftOf(viewModel))
    }

    @Test
    fun loggedInUserChange_afterSent_opensEmptyForm() {
        val viewModel = createViewModel()
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()

        accountRepository.logInAs(OTHER_USER_UID)

        assertEquals(EMPTY_DRAFT, draftOf(viewModel))
    }

    @Test
    fun loggedInUserChange_whileSending_neverConfirmsToNewUser() {
        val sendCompletion = CompletableDeferred<Unit>()
        reportRepository.sendCompletion = sendCompletion
        val viewModel = createViewModel()
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()

        accountRepository.logInAs(OTHER_USER_UID)
        sendCompletion.complete(Unit)

        assertEquals(EMPTY_DRAFT, draftOf(viewModel))
    }

    @Test
    fun create_afterProcessDeath_restoresDraft() {
        val viewModel = createViewModel()
        viewModel.chooseReason(ReportReason.VIOLENCE)
        viewModel.changeDetails(DETAILS)

        val restored = createViewModel()

        val expected = Draft(ReportReason.VIOLENCE, DETAILS, ReportStatus.EDITING)
        assertEquals(expected, draftOf(restored))
    }

    @Test
    fun create_afterProcessDeathOnceSent_confirmsSent() {
        val viewModel = createViewModel()
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()

        assertEquals(ReportStatus.SENT, createViewModel().status)
    }

    @Test
    fun create_afterProcessDeathOnceDiscarded_opensEmptyForm() {
        val viewModel = createViewModel()
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.changeDetails(DETAILS)
        viewModel.send()
        viewModel.discardDraft()

        assertEquals(EMPTY_DRAFT, draftOf(createViewModel()))
    }

    private fun createViewModel(
        reportedUid: String = REPORTED_UID,
        postId: String = POST_ID,
        accountRepository: AccountRepository = this.accountRepository
    ) = ReportViewModel(
        reportedUid = reportedUid,
        postId = postId,
        reportRepository = reportRepository,
        accountRepository = accountRepository,
        savedStateHandle = savedStateHandle
    )

    private fun draftOf(viewModel: ReportViewModel) =
        Draft(viewModel.reason, viewModel.details, viewModel.status)
}

// What the report dialog shows, compared as one value.
private data class Draft(val reason: ReportReason?, val details: String, val status: ReportStatus)

private val EMPTY_DRAFT = Draft(reason = null, details = "", status = ReportStatus.EDITING)
