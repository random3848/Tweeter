package edu.csuci.tweeter

import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.PostRepository

/**
 * The objects the whole app shares, created once by [TweeterApplication].
 *
 * Screens get these through their ViewModel factories instead of creating their own, so
 * replacing an implementation (for example a Firebase [PostRepository] for the fake one) only
 * changes this class.
 */
class AppContainer {
    /** Every post in the app. */
    val postRepository: PostRepository = FakePostRepository()
}
