package edu.csuci.tweeter.data

/** Thrown by [BookmarkRepository] when saving or unsaving a post can't be saved. */
class BookmarkException(cause: Throwable) : Exception("Could not change the bookmark", cause)
