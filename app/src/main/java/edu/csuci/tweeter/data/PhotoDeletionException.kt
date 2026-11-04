package edu.csuci.tweeter.data

/** An authentication or Storage failure while deleting an uploaded photo. */
class PhotoDeletionException(cause: Throwable) : Exception("Could not delete photo", cause)
