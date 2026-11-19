package edu.csuci.tweeter.data

import com.google.firebase.FirebaseException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private const val REPORTS = "reports"
private const val REPORTER_UID = "reporterUid"
private const val REPORTED_UID = "reportedUid"
private const val POST_ID = "postId"
private const val REASON = "reason"
private const val DETAILS = "details"
private const val TIMESTAMP = "timestamp"

/**
 * Reports stored in Firestore at reports/{reportId}, which only the Firebase console reads. The
 * ID is [Report.id], so reporting the same post or user again replaces the earlier report.
 *
 * @param firestore Stores the reports.
 */
class FirestoreReportRepository(private val firestore: FirebaseFirestore) : ReportRepository {
    override suspend fun sendReport(report: Report) {
        val fields = mapOf(
            REPORTER_UID to report.reporterUid,
            REPORTED_UID to report.reportedUid,
            POST_ID to report.postId,
            REASON to report.reason.name,
            DETAILS to report.details,
            TIMESTAMP to FieldValue.serverTimestamp()
        )
        try {
            firestore.collection(REPORTS).document(report.id).set(fields).await()
        } catch (error: FirebaseException) {
            throw ReportException(error)
        }
    }
}
