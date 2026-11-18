package edu.csuci.tweeter.data

/**
 * A failure to read or add comments.
 *
 * @param cause Why it failed.
 */
class CommentException(cause: Throwable) : Exception("Could not read or add comments", cause)
