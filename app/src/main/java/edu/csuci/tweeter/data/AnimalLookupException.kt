package edu.csuci.tweeter.data

/**
 * Thrown when animals can't be looked up, for example because iNaturalist can't be reached or
 * answered with something the app can't read. Trying again later may work.
 */
class AnimalLookupException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
