@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.report

import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeReportRepository
import edu.csuci.tweeter.data.Report
import edu.csuci.tweeter.data.ReportReason
import edu.csuci.tweeter.data.ReportTarget
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
private const val OTHER_POST_ID = "otherPost"
private const val OTHER_USER_UID = "sam"
private const val DETAILS = "Kicked a cat"

private val POST_TARGET = ReportTarget(reportedUid = REPORTED_UID, postId = POST_ID)
private val OTHER_POST_TARGET = ReportTarget(reportedUid = REPORTED_UID, postId = OTHER_POST_ID)
private val PROFILE_TARGET = ReportTarget(reportedUid = REPORTED_UID, postId = Report.NO_POST)

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
        val viewModel = createReporting(POST_TARGET)
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
        val viewModel = createReporting(PROFILE_TARGET)
        viewModel.chooseReason(ReportReason.HARASSMENT)

        viewModel.send()

        assertEquals("", reportRepository.reports.single().postId)
        assertEquals(REPORTED_UID, reportRepository.reports.single().reportedUid)
    }

    @Test
    fun send_withoutReason_sendsNothing() {
        val viewModel = createReporting(POST_TARGET)

        viewModel.send()

        assertTrue(reportRepository.reports.isEmpty())
        assertEquals(ReportStatus.EDITING, viewModel.status)
    }

    @Test
    fun send_beforeReportStarted_sendsNothing() {
        val viewModel = createViewModel()
        viewModel.chooseReason(ReportReason.SPAM)

        viewModel.send()

        assertTrue(reportRepository.reports.isEmpty())
        assertEquals(ReportStatus.EDITING, viewModel.status)
    }

    @Test
    fun send_beforeServerAnswers_showsSending() {
        reportRepository.sendCompletion = CompletableDeferred()
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)

        viewModel.send()

        assertEquals(ReportStatus.SENDING, viewModel.status)
    }

    @Test
    fun send_failure_showsFailedAndRetrySends() {
        reportRepository.failsToSend = true
        val viewModel = createReporting(POST_TARGET)
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
        val viewModel = createReporting(POST_TARGET, accountRepository = FakeAccountRepository())
        viewModel.chooseReason(ReportReason.SPAM)

        viewModel.send()

        assertTrue(reportRepository.reports.isEmpty())
    }

    @Test
    fun send_ownContent_sendsNothing() {
        val viewModel = createReporting(ReportTarget(reportedUid = REPORTER_UID, postId = POST_ID))
        viewModel.chooseReason(ReportReason.SPAM)

        viewModel.send()

        assertTrue(reportRepository.reports.isEmpty())
    }

    @Test
    fun changeDetails_tooLong_isCut() {
        val viewModel = createReporting(POST_TARGET)

        viewModel.changeDetails("a".repeat(Report.MAX_DETAILS_LENGTH + 10))

        assertEquals(Report.MAX_DETAILS_LENGTH, viewModel.details.length)
    }

    @Test
    fun send_twiceWhileSending_sendsOneReport() {
        val sendCompletion = CompletableDeferred<Unit>()
        reportRepository.sendCompletion = sendCompletion
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)

        viewModel.send()
        viewModel.send()
        sendCompletion.complete(Unit)

        assertEquals(1, reportRepository.reports.size)
    }

    @Test
    fun discardDraft_afterSent_opensEmptyForm() {
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.changeDetails(DETAILS)
        viewModel.send()

        viewModel.discardDraft()

        assertEquals(EMPTY_DRAFT, draftOf(viewModel))
    }

    @Test
    fun discardDraft_unsentDraft_opensEmptyForm() {
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.changeDetails(DETAILS)

        viewModel.discardDraft()

        assertEquals(EMPTY_DRAFT, draftOf(viewModel))
    }

    @Test
    fun discardDraft_whileSending_keepsSendingAndConfirmsLater() {
        val sendCompletion = CompletableDeferred<Unit>()
        reportRepository.sendCompletion = sendCompletion
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()

        viewModel.discardDraft()
        sendCompletion.complete(Unit)

        assertEquals(ReportStatus.SENT, viewModel.status)
    }

    @Test
    fun startReport_sameTargetWhileSending_showsSending() {
        reportRepository.sendCompletion = CompletableDeferred()
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()
        viewModel.discardDraft()

        viewModel.startReport(POST_TARGET)

        assertEquals(ReportStatus.SENDING, viewModel.status)
    }

    @Test
    fun startReport_otherTarget_opensEmptyForm() {
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.changeDetails(DETAILS)

        viewModel.startReport(OTHER_POST_TARGET)

        assertEquals(EMPTY_DRAFT, draftOf(viewModel))
    }

    @Test
    fun startReport_otherTarget_sendsAboutIt() {
        val viewModel = createReporting(POST_TARGET)
        viewModel.startReport(OTHER_POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)

        viewModel.send()

        assertEquals(OTHER_POST_ID, reportRepository.reports.single().postId)
    }

    @Test
    fun startReport_otherTargetWhileSending_neverConfirmsOtherTarget() {
        val sendCompletion = CompletableDeferred<Unit>()
        reportRepository.sendCompletion = sendCompletion
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()
        viewModel.discardDraft()

        viewModel.startReport(OTHER_POST_TARGET)
        sendCompletion.complete(Unit)

        assertEquals(EMPTY_DRAFT, draftOf(viewModel))
    }

    @Test
    fun loggedInUserChange_clearsDraft() {
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.changeDetails(DETAILS)

        accountRepository.logInAs(OTHER_USER_UID)

        assertEquals(EMPTY_DRAFT, draftOf(viewModel))
    }

    @Test
    fun loggedInUserChange_afterSent_opensEmptyForm() {
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()

        accountRepository.logInAs(OTHER_USER_UID)

        assertEquals(EMPTY_DRAFT, draftOf(viewModel))
    }

    @Test
    fun loggedInUserChange_whileSending_neverConfirmsToNewUser() {
        val sendCompletion = CompletableDeferred<Unit>()
        reportRepository.sendCompletion = sendCompletion
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()

        accountRepository.logInAs(OTHER_USER_UID)
        sendCompletion.complete(Unit)

        assertEquals(EMPTY_DRAFT, draftOf(viewModel))
    }

    @Test
    fun create_afterProcessDeath_restoresDraft() {
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.VIOLENCE)
        viewModel.changeDetails(DETAILS)

        val restored = createViewModel()

        val expected = Draft(ReportReason.VIOLENCE, DETAILS, ReportStatus.EDITING)
        assertEquals(expected, draftOf(restored))
    }

    @Test
    fun create_afterProcessDeath_sendsAboutSameTarget() {
        val viewModel = createReporting(OTHER_POST_TARGET)
        viewModel.chooseReason(ReportReason.VIOLENCE)
        val restored = createViewModel()

        restored.send()

        assertEquals(OTHER_POST_ID, reportRepository.reports.single().postId)
    }

    @Test
    fun create_afterProcessDeathOnceSent_confirmsSent() {
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()

        assertEquals(ReportStatus.SENT, createViewModel().status)
    }

    // Cancel while the server hasn't answered, then the app is stopped: Firestore still sends
    // the queued report, so the restored dialog mustn't offer it as a draft to send again.
    @Test
    fun create_afterProcessDeathWhileSending_showsSending() {
        reportRepository.sendCompletion = CompletableDeferred()
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()
        viewModel.discardDraft()

        val restored = createViewModel()

        assertEquals(ReportStatus.SENDING, restored.status)
    }

    @Test
    fun create_afterProcessDeathWhileSending_confirmsOnceServerAnswers() {
        val sendCompletion = CompletableDeferred<Unit>()
        reportRepository.sendCompletion = sendCompletion
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()
        val restored = createViewModel()

        sendCompletion.complete(Unit)

        assertEquals(ReportStatus.SENT, restored.status)
    }

    @Test
    fun create_afterProcessDeathWhileSendingThenLoggedOut_opensEmptyForm() {
        reportRepository.sendCompletion = CompletableDeferred()
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.send()

        val restored = createViewModel(accountRepository = FakeAccountRepository())

        assertEquals(EMPTY_DRAFT, draftOf(restored))
    }

    @Test
    fun create_afterProcessDeathOnceDiscarded_opensEmptyForm() {
        val viewModel = createReporting(POST_TARGET)
        viewModel.chooseReason(ReportReason.SPAM)
        viewModel.changeDetails(DETAILS)
        viewModel.send()
        viewModel.discardDraft()

        assertEquals(EMPTY_DRAFT, draftOf(createViewModel()))
    }

    private fun createReporting(
        target: ReportTarget,
        accountRepository: AccountRepository = this.accountRepository
    ) = createViewModel(accountRepository).apply { startReport(target) }

    // A new ViewModel on the same saved state, as when the app is restored after process death.
    private fun createViewModel(accountRepository: AccountRepository = this.accountRepository) =
        ReportViewModel(
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
