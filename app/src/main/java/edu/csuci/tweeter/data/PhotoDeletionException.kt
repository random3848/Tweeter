package edu.csuci.tweeter.data

/**
 * An authentication or Storage failure while deleting an uploaded photo, or a URL that isn't an
 * uploaded photo's.
 */
class PhotoDeletionException(cause: Throwable) : Exception("Could not delete photo", cause)
