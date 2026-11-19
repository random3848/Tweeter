package edu.csuci.tweeter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

private const val REPORTER_UID = "maya"
private const val REPORTED_UID = "troll"
private const val POST_ID = "post"

class ReportTest {
    // firestore.rules builds the same ID from the report's fields and rejects any other.
    @Test
    fun id_postReport_namesReporterReportedUserAndPost() {
        assertEquals("maya_troll_post", reportOf(postId = POST_ID).id)
    }

    @Test
    fun id_profileReport_endsWithProfile() {
        assertEquals("maya_troll_profile", reportOf(postId = Report.NO_POST).id)
    }

    @Test
    fun id_repeatReportWithOtherReasonAndDetails_isTheSame() {
        val first = reportOf(postId = POST_ID)

        val repeat = first.copy(reason = ReportReason.OTHER, details = "Still spamming")

        assertEquals(first.id, repeat.id)
    }

    @Test
    fun id_postAndProfileReports_differ() {
        assertNotEquals(reportOf(postId = POST_ID).id, reportOf(postId = Report.NO_POST).id)
    }

    private fun reportOf(postId: String) = Report(
        reporterUid = REPORTER_UID,
        reportedUid = REPORTED_UID,
        postId = postId,
        reason = ReportReason.SPAM,
        details = ""
    )
}
