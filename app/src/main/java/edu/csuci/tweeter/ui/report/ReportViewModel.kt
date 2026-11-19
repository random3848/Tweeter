package edu.csuci.tweeter.ui.report

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
import edu.csuci.tweeter.data.Report
import edu.csuci.tweeter.data.ReportException
import edu.csuci.tweeter.data.ReportReason
import edu.csuci.tweeter.data.ReportRepository
import kotlinx.coroutines.launch

private const val TAG = "ReportViewModel"

/** Where a report stands in the report dialog. */
enum class ReportStatus {
    /** Choosing a reason and writing details. */
    EDITING,

    /** Sending the report. */
    SENDING,

    /** Stored; the dialog confirms it. */
    SENT,

    /** Failed to send; the dialog says so and the user can send again. */
    FAILED
}

/**
 * The report dialog's draft, and sending it as the logged-in user's report.
 *
 * @param reportedUid The user reported, or the reported post's author.
 * @param postId The reported post's ID, or [Report.NO_POST] for a profile.
 * @param reportRepository Where the report is sent.
 * @param accountRepository Tells who is reporting.
 */
class ReportViewModel(
    private val reportedUid: String,
    private val postId: String,
    private val reportRepository: ReportRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {
    /** The chosen reason, or null until the user chooses one, which Send needs. */
    var reason by mutableStateOf<ReportReason?>(null)
        private set

    /** The optional details being typed. */
    var details by mutableStateOf("")
        private set

    /** Where the report stands. */
    var status by mutableStateOf(ReportStatus.EDITING)
        private set

    /** Chooses [reason] as why the user reports. */
    fun chooseReason(reason: ReportReason) {
        this.reason = reason
    }

    /** Replaces the details with [text], cut to [Report.MAX_DETAILS_LENGTH]. */
    fun changeDetails(text: String) {
        details = text.take(Report.MAX_DETAILS_LENGTH)
    }

    /**
     * Sends the report as the logged-in user's. Does nothing without a reason, while sending,
     * for guests, or when the user would report themselves.
     */
    fun send() {
        val report = reportOrNull() ?: return
        status = ReportStatus.SENDING
        viewModelScope.launch { sendOrFail(report) }
    }

    private fun reportOrNull(): Report? {
        val chosenReason = reason ?: return null
        val reporterUid = accountRepository.loggedInUid.value ?: return null
        if (reporterUid == reportedUid || status == ReportStatus.SENDING) {
            return null
        }
        return Report(
            reporterUid = reporterUid,
            reportedUid = reportedUid,
            postId = postId,
            reason = chosenReason,
            details = details.trim()
        )
    }

    private suspend fun sendOrFail(report: Report) {
        status = try {
            reportRepository.sendReport(report)
            ReportStatus.SENT
        } catch (error: ReportException) {
            Log.e(TAG, "Could not send the report", error)
            ReportStatus.FAILED
        }
    }

    companion object {
        /**
         * Creates a [ReportViewModel] reporting user [reportedUid], about the post with [postId]
         * or, given [Report.NO_POST], about their profile.
         */
        fun createFactory(reportedUid: String, postId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = this[APPLICATION_KEY] as TweeterApplication
                    ReportViewModel(
                        reportedUid = reportedUid,
                        postId = postId,
                        reportRepository = application.container.reportRepository,
                        accountRepository = application.container.accountRepository
                    )
                }
            }
    }
}
