package edu.csuci.tweeter

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.AnimalRepository
import edu.csuci.tweeter.data.FakeAnimalRepository
import edu.csuci.tweeter.data.FirebaseAccountRepository
import edu.csuci.tweeter.data.FirebasePhotoRepository
import edu.csuci.tweeter.data.FirestorePostRepository
import edu.csuci.tweeter.data.FirestoreProfileRepository
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.location.LocationTagRemover
import edu.csuci.tweeter.ui.navigation.ShowPostRequest

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
 *         MapViewModel(
 *             postRepository = application.container.postRepository,
 *             showOnMapRequest = application.container.showOnMapRequest,
 *             showInFeedRequest = application.container.showInFeedRequest
 *         )
 *     }
 * }
 * ```
 * and its screen creates it with `viewModel(factory = MapViewModel.factory)`, from
 * `androidx.lifecycle:lifecycle-viewmodel-compose`. `ui/map/MapViewModel.kt` is this example.
 *
 * @param context Any context; objects that need one keep only its application context.
 */
class AppContainer(private val context: Context) {
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
     * Shared authentication operations and the registered user's identity.
     */
    val accountRepository: AccountRepository by lazy {
        FirebaseAccountRepository(auth = FirebaseAuth.getInstance())
    }

    /** Uploads post and profile photos to Firebase Storage. */
    val photoRepository: PhotoRepository by lazy {
        FirebasePhotoRepository(
            auth = FirebaseAuth.getInstance(),
            storage = FirebaseStorage.getInstance(),
            locationTagRemover = LocationTagRemover(context)
        )
    }

    /**
     * The animals a post can be tagged with: a fixed list in memory until the iNaturalist
     * repository is built; that work replaces this.
     */
    val animalRepository: AnimalRepository = FakeAnimalRepository()

    /** The post Feed asks Map to show. Map opens the post's popup, then clears the request. */
    val showOnMapRequest: ShowPostRequest = ShowPostRequest()

    /** The post Map's popup asks Feed to show. Feed scrolls to it, then clears the request. */
    val showInFeedRequest: ShowPostRequest = ShowPostRequest()
}
