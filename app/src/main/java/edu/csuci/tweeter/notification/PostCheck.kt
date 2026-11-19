package edu.csuci.tweeter.notification

/** A check for posts to notify the user of, which a [PostCheckWorker] runs every so often. */
interface PostCheck {
    /**
     * Notifies the user of the posts this kind of check is about, each once.
     *
     * @throws edu.csuci.tweeter.data.PostLookupException If the posts can't be looked up; the
     *     worker then runs the check again later.
     */
    suspend fun run()
}
