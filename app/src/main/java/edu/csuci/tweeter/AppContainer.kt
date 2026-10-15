package edu.csuci.tweeter

import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.PostRepository

/**
 * The objects the whole app shares, created once by [TweeterApplication].
 *
 * Screens get these through their ViewModel factories instead of creating their own, so
 * replacing an implementation (for example a Firebase [PostRepository] for the fake one) only
 * changes this class.
 *
 * A ViewModel gets the repository through a factory in its companion object:
 * ```
 * val factory = viewModelFactory {
 *     initializer {
 *         val application = this[APPLICATION_KEY] as TweeterApplication
 *         MapViewModel(application.container.postRepository)
 *     }
 * }
 * ```
 * and its screen creates it with `viewModel(factory = MapViewModel.factory)`. That function
 * comes from `androidx.lifecycle:lifecycle-viewmodel-compose`; the first screen with a
 * ViewModel adds it to `gradle/libs.versions.toml` and `app/build.gradle.kts`.
 */
class AppContainer {
    /** Every post in the app. */
    val postRepository: PostRepository = FakePostRepository()
}
