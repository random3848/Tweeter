package edu.csuci.tweeter.data

/** A [ReportRepository] in memory for tests, keeping every report it was sent, in order. */
class FakeReportRepository : ReportRepository {
    /** The reports sent so far. */
    val reports = mutableListOf<Report>()

    /** Whether sending a report fails, as when the server rejects it. */
    var failsToSend = false

    override suspend fun sendReport(report: Report) {
        if (failsToSend) {
            throw ReportException(IllegalStateException("The server rejected the report"))
        }
        reports += report
    }
}
