package edu.csuci.tweeter.ui.report

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.Report
import edu.csuci.tweeter.data.ReportException
import edu.csuci.tweeter.data.ReportReason
import edu.csuci.tweeter.data.ReportRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private const val TAG = "ReportViewModel"
private const val REASON_KEY = "reason"
private const val DETAILS_KEY = "details"
private const val IS_SENT_KEY = "isSent"

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
 * The draft and whether it was sent survive the app being stopped in the background, through
 * [savedStateHandle]. It's cleared when another user logs in or the user logs out.
 *
 * @param reportedUid The user reported, or the reported post's author.
 * @param postId The reported post's ID, or [Report.NO_POST] for a profile.
 * @param reportRepository Where the report is sent.
 * @param accountRepository Tells who is reporting.
 * @param savedStateHandle Keeps the reason, the details and whether the report was sent.
 */
class ReportViewModel(
    private val reportedUid: String,
    private val postId: String,
    private val reportRepository: ReportRepository,
    private val accountRepository: AccountRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private var sendJob: Job? = null

    /** The chosen reason, or null until the user chooses one, which Send needs. */
    var reason by mutableStateOf(
        savedStateHandle.get<String>(REASON_KEY)?.let(ReportReason::valueOf)
    )
        private set

    /** The optional details being typed. */
    var details by mutableStateOf(savedStateHandle.get<String>(DETAILS_KEY) ?: "")
        private set

    /** Where the report stands. */
    var status by mutableStateOf(
        if (savedStateHandle.get<Boolean>(IS_SENT_KEY) == true) {
            ReportStatus.SENT
        } else {
            ReportStatus.EDITING
        }
    )
        private set

    init {
        viewModelScope.launch {
            // The first value is the user the draft belongs to; each later one is another user.
            accountRepository.loggedInUid.drop(1).collect { clearDraftForNewUser() }
        }
    }

    /** Chooses [reason] as why the user reports. */
    fun chooseReason(reason: ReportReason) {
        updateReason(reason)
    }

    /** Replaces the details with [text], cut to [Report.MAX_DETAILS_LENGTH]. */
    fun changeDetails(text: String) {
        updateDetails(text.take(Report.MAX_DETAILS_LENGTH))
    }

    /**
     * Sends the report as the logged-in user's. Does nothing without a reason, while sending,
     * for guests, or when the user would report themselves.
     */
    fun send() {
        val report = reportOrNull() ?: return
        updateStatus(ReportStatus.SENDING)
        sendJob = viewModelScope.launch { sendOrFail(report) }
    }

    /**
     * Discards the draft, or the confirmation once sent, so the dialog opens empty next time.
     * Does nothing while the report is being sent: the dialog shows how that ends when reopened.
     */
    fun discardDraft() {
        if (status != ReportStatus.SENDING) {
            clearDraft()
        }
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
        val outcome = try {
            reportRepository.sendReport(report)
            ReportStatus.SENT
        } catch (error: ReportException) {
            Log.e(TAG, "Could not send the report", error)
            ReportStatus.FAILED
        }
        updateStatus(outcome)
    }

    // The previous user's report may still be sending; the new user mustn't see how it ends.
    private fun clearDraftForNewUser() {
        sendJob?.cancel()
        clearDraft()
    }

    private fun clearDraft() {
        updateReason(null)
        updateDetails("")
        updateStatus(ReportStatus.EDITING)
    }

    private fun updateReason(newReason: ReportReason?) {
        reason = newReason
        savedStateHandle[REASON_KEY] = newReason?.name
    }

    private fun updateDetails(newDetails: String) {
        details = newDetails
        savedStateHandle[DETAILS_KEY] = newDetails
    }

    private fun updateStatus(newStatus: ReportStatus) {
        status = newStatus
        savedStateHandle[IS_SENT_KEY] = newStatus == ReportStatus.SENT
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
                        accountRepository = application.container.accountRepository,
                        savedStateHandle = createSavedStateHandle()
                    )
                }
            }
    }
}
