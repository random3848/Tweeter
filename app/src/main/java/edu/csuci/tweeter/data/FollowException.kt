package edu.csuci.tweeter.data

/**
 * A failure to read or change a follower relationship.
 *
 * @param cause The underlying reason the operation failed.
 */
class FollowException(cause: Throwable) :
    Exception("Could not read or change the follower relationship", cause)
