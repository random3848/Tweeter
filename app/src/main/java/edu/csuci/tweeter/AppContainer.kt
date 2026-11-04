package edu.csuci.tweeter

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import edu.csuci.tweeter.data.FirebasePhotoRepository
import edu.csuci.tweeter.data.FirestorePostRepository
import edu.csuci.tweeter.data.FirestoreProfileRepository
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PlaceholderAccountRepository
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.ProfileRepository

/**
 * The objects the whole app shares, created once by [TweeterApplication].
 *
 * Screens get these through their ViewModel factories instead of creating their own, so
 * replacing an implementation (for example the in-memory `FakePostRepository` for Firestore while
 * working offline) only changes this class.
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
 * and its screen creates it with `viewModel(factory = MapViewModel.factory)`, from
 * `androidx.lifecycle:lifecycle-viewmodel-compose`. `ui/map/MapViewModel.kt` is this example.
 */
class AppContainer {
    /** Every post in the app, stored in Firestore. */
    val postRepository: PostRepository by lazy {
        FirestorePostRepository(
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance()
        )
    }

    /** Every user's profile, stored in Firestore. */
    val profileRepository: ProfileRepository by lazy {
        FirestoreProfileRepository(firestore = FirebaseFirestore.getInstance())
    }

    /**
     * Who is logged in. A placeholder until create-account/login is built; that work replaces it
     * here. It is typed as the placeholder so Profile's log-in prompt can call
     * [PlaceholderAccountRepository.logIn]; everything else uses it as an `AccountRepository`.
     */
    val accountRepository: PlaceholderAccountRepository by lazy {
        PlaceholderAccountRepository(auth = FirebaseAuth.getInstance())
    }

    /** Uploads camera photos to Firebase Storage. */
    val photoRepository: PhotoRepository by lazy {
        FirebasePhotoRepository(
            auth = FirebaseAuth.getInstance(),
            storage = FirebaseStorage.getInstance()
        )
    }
}
