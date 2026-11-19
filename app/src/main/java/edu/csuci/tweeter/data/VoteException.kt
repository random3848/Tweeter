package edu.csuci.tweeter.data

/**
 * Thrown by [VoteRepository] when a change to the votes on a post's animal, or the poster's
 * reclassifying of it, can't be saved.
 */
class VoteException(cause: Throwable) : Exception("Could not change the animal votes", cause)
