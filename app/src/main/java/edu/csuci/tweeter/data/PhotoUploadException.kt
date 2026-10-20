package edu.csuci.tweeter.data

/** An authentication or file-transfer failure while uploading a photo. */
class PhotoUploadException(cause: Throwable) : Exception("Could not upload photo", cause)
