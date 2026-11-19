@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.report

import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeReportRepository
import edu.csuci.tweeter.data.Report
import edu.csuci.tweeter.data.ReportReason
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

class ReportViewModelTest {
    private val reportRepository = FakeReportRepository()
    private val accountRepository = FakeAccountRepository(REPORTER_UID)

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
        val viewModel = ReportViewModel(
            reportedUid = REPORTED_UID,
            postId = POST_ID,
            reportRepository = reportRepository,
            accountRepository = FakeAccountRepository()
        )
        viewModel.chooseReason(ReportReason.SPAM)

        viewModel.send()

        assertTrue(reportRepository.reports.isEmpty())
    }

    @Test
    fun send_ownContent_sendsNothing() {
        val viewModel = ReportViewModel(
            reportedUid = REPORTER_UID,
            postId = POST_ID,
            reportRepository = reportRepository,
            accountRepository = accountRepository
        )
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

    private fun createViewModel(postId: String = POST_ID) = ReportViewModel(
        reportedUid = REPORTED_UID,
        postId = postId,
        reportRepository = reportRepository,
        accountRepository = accountRepository
    )
}
