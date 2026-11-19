package edu.csuci.tweeter.data

/** Thrown by [PostLookup] when posts can't be looked up, such as while the device is offline. */
class PostLookupException(cause: Throwable) : Exception("Could not look up posts", cause)
