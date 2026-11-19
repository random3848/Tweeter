package edu.csuci.tweeter.data

import kotlinx.coroutines.CompletableDeferred

/** A [ReportRepository] in memory for tests, keeping every report it was sent, in order. */
class FakeReportRepository : ReportRepository {
    /** The reports sent so far. */
    val reports = mutableListOf<Report>()

    /** Whether sending a report fails, as when the server rejects it. */
    var failsToSend = false

    /** When set, each send waits for it to complete before storing, as on a slow network. */
    var sendCompletion: CompletableDeferred<Unit>? = null

    override suspend fun sendReport(report: Report) {
        sendCompletion?.await()
        if (failsToSend) {
            throw ReportException(IllegalStateException("The server rejected the report"))
        }
        reports += report
    }
}
