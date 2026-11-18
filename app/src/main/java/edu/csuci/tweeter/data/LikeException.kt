package edu.csuci.tweeter.data

/** Thrown by [LikeRepository] when liking or unliking a post can't be saved. */
class LikeException(cause: Throwable) : Exception("Could not change the like", cause)
