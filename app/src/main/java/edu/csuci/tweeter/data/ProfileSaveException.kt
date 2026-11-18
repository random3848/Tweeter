package edu.csuci.tweeter.data

/** A failure to save a profile, such as the server rejecting it. */
class ProfileSaveException(cause: Throwable) : Exception("Could not save the profile", cause)
