package edu.csuci.tweeter.data

/** Where reports of posts and users are sent. The app can't read them back. */
interface ReportRepository {
    /** Stores [report] for the team to review. Fails with [ReportException]. */
    suspend fun sendReport(report: Report)
}
