package edu.csuci.tweeter.data

/**
 * A failure to send a report.
 *
 * @param cause Why it failed.
 */
class ReportException(cause: Throwable) : Exception("Could not send the report", cause)
