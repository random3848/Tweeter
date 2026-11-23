package edu.csuci.tweeter.data

/**
 * What a report is about: one of a user's posts, or their profile.
 *
 * @property reportedUid The user reported, or the reported post's author.
 * @property postId The reported post's ID, or [Report.NO_POST] for a profile.
 */
data class ReportTarget(val reportedUid: String, val postId: String)
