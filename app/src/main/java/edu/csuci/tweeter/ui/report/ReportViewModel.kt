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
import edu.csuci.tweeter.data.ReportTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private const val TAG = "ReportViewModel"
private const val REPORTED_UID_KEY = "reportedUid"
private const val POST_ID_KEY = "postId"
private const val REASON_KEY = "reason"
private const val DETAILS_KEY = "details"
private const val STATUS_KEY = "status"

/** Where a report stands in the report dialog. */
enum class ReportStatus {
    /** Choosing a reason and writing details. */
    EDITING,

    /**
     * Waiting for the server to store the report. Offline, Firestore keeps it queued, also after
     * the app is stopped, and sends it once back online.
     */
    SENDING,

    /** Stored; the dialog confirms it. */
    SENT,

    /** Failed to send; the dialog says so and the user can send again. */
    FAILED
}

/**
 * The report dialogs of one screen: the draft of the post or user being reported, and sending it
 * as the logged-in user's report. The screen tells it which with [startReport], so it keeps one
 * draft however many posts the screen shows.
 *
 * The draft, what it reports and where it stands survive the app being stopped in the background,
 * through [savedStateHandle]; a report that was still sending is sent again then, which replaces
 * the same report, so the user learns how it ends. It's cleared when another user logs in or the
 * user logs out.
 *
 * @param reportRepository Where the report is sent.
 * @param accountRepository Tells who is reporting.
 * @param savedStateHandle Keeps what is reported, the reason, the details and the status.
 */
class ReportViewModel(
    private val reportRepository: ReportRepository,
    private val accountRepository: AccountRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private var target = restoredTarget()
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
        savedStateHandle.get<String>(STATUS_KEY)?.let(ReportStatus::valueOf) ?: ReportStatus.EDITING
    )
        private set

    init {
        if (status == ReportStatus.SENDING) {
            resendRestoredReport()
        }
        viewModelScope.launch {
            // The first value is the user the draft belongs to; each later one is another user.
            accountRepository.loggedInUid.drop(1).collect { abandonReport() }
        }
    }

    /**
     * Starts reporting [newTarget] with an empty draft, unless the draft is already about it, as
     * when the dialog reopens while its report is still sending.
     */
    fun startReport(newTarget: ReportTarget) {
        if (newTarget != target) {
            updateTarget(newTarget)
            abandonReport()
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
     * Sends the report as the logged-in user's. Does nothing before [startReport], without a
     * reason, while sending, for guests, or when the user would report themselves.
     */
    fun send() {
        if (status == ReportStatus.SENDING) {
            return
        }
        reportOrNull()?.let(::startSending)
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

    // The app was stopped while the report was sending. Firestore may still hold it queued or may
    // have stored it; sending the same report again replaces it either way and tells how it ends.
    private fun resendRestoredReport() {
        val report = reportOrNull()
        if (report == null) {
            clearDraft()
        } else {
            startSending(report)
        }
    }

    private fun reportOrNull(): Report? {
        val reportTarget = target ?: return null
        val chosenReason = reason ?: return null
        val reporterUid = accountRepository.loggedInUid.value ?: return null
        if (reporterUid == reportTarget.reportedUid) {
            return null
        }
        return Report(
            reporterUid = reporterUid,
            reportedUid = reportTarget.reportedUid,
            postId = reportTarget.postId,
            reason = chosenReason,
            details = details.trim()
        )
    }

    private fun startSending(report: Report) {
        updateStatus(ReportStatus.SENDING)
        sendJob = viewModelScope.launch { sendOrFail(report) }
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

    // Stops waiting for a report still sending, which Firestore sends anyway, so that how it ends
    // isn't shown for another user or another post, and clears the draft.
    private fun abandonReport() {
        sendJob?.cancel()
        clearDraft()
    }

    private fun clearDraft() {
        updateReason(null)
        updateDetails("")
        updateStatus(ReportStatus.EDITING)
    }

    private fun restoredTarget(): ReportTarget? {
        val reportedUid = savedStateHandle.get<String>(REPORTED_UID_KEY) ?: return null
        val postId = checkNotNull(savedStateHandle.get<String>(POST_ID_KEY)) {
            "The reported post's ID is saved with the reported user's"
        }
        return ReportTarget(reportedUid = reportedUid, postId = postId)
    }

    private fun updateTarget(newTarget: ReportTarget) {
        target = newTarget
        savedStateHandle[REPORTED_UID_KEY] = newTarget.reportedUid
        savedStateHandle[POST_ID_KEY] = newTarget.postId
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
        savedStateHandle[STATUS_KEY] = newStatus.name
    }

    companion object {
        /**
         * Creates the screen's [ReportViewModel]. Screens get it without a key, so each has one,
         * whose dialog [ReportViewModel.startReport] points at the post or user reported.
         */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                ReportViewModel(
                    reportRepository = application.container.reportRepository,
                    accountRepository = application.container.accountRepository,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
