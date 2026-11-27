package edu.csuci.tweeter

/**
 * The User-Agent of the app's own HTTP requests, `Tweeter/<version> (+<project URL>)`, so that
 * the operators of the servers it downloads from, such as OpenStreetMap's and iNaturalist's, can
 * find who runs it.
 */
const val HTTP_USER_AGENT =
    "Tweeter/${BuildConfig.VERSION_NAME} (+https://github.com/random3848/Tweeter)"
