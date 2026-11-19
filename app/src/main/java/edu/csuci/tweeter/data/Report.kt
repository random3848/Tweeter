package edu.csuci.tweeter.data

/**
 * A logged-in user's report of a post or of another user, which the team reviews in the Firebase
 * console.
 *
 * @property reporterUid The ID of the user sending the report.
 * @property reportedUid The ID of the user reported, or of the reported post's author.
 * @property postId The reported post's ID, or [NO_POST] when a profile is reported.
 * @property reason Why the user reports it.
 * @property details What the user adds, at most [MAX_DETAILS_LENGTH] characters; may be empty.
 */
data class Report(
    val reporterUid: String,
    val reportedUid: String,
    val postId: String,
    val reason: ReportReason,
    val details: String
) {
    /** The limits and defaults of reports. */
    companion object {
        /** The longest [details] can be; firestore.rules checks the same number. */
        const val MAX_DETAILS_LENGTH = 500

        /** The [postId] of a report about a user's profile rather than one of their posts. */
        const val NO_POST = ""
    }
}
