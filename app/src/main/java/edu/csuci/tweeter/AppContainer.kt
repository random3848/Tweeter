package edu.csuci.tweeter

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.FirebaseAccountRepository
import edu.csuci.tweeter.data.FirebasePhotoRepository
import edu.csuci.tweeter.data.FirestorePostRepository
import edu.csuci.tweeter.data.FirestoreProfileRepository
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.location.LocationTagRemover
import edu.csuci.tweeter.ui.WhileAppVisible
import edu.csuci.tweeter.ui.map.startMapEngine
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
 *         FeedViewModel(
 *             postRepository = application.container.postRepository,
 *             showInFeedRequest = application.container.showInFeedRequest,
 *             showOnMapRequest = application.container.showOnMapRequest,
 *             sharing = application.container.whileAppVisible
 *         )
 *     }
 * }
 * ```
 * and its screen creates it with `viewModel(factory = FeedViewModel.factory)`, from
 * `androidx.lifecycle:lifecycle-viewmodel-compose`. `ui/feed/FeedViewModel.kt` is this example.
 *
 * @param context Any context; objects that need one keep only its application context.
 */
class AppContainer(private val context: Context) {
    // Lives as long as the app; runs the listeners repositories share between screens, and the
    // creation of the repositories ahead of the first screen.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Every post in the app, stored in Firestore. Its post table is one listener shared by every
     * screen, parsed on [Dispatchers.Default].
     */
    val postRepository: PostRepository by lazy {
        FirestorePostRepository(
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance(),
            scope = applicationScope,
            dispatcher = Dispatchers.Default
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
            locationTagRemover = LocationTagRemover(context, ioDispatcher = Dispatchers.IO)
        )
    }

    /** The post Feed asks Map to show. Map opens the post's popup, then clears the request. */
    val showOnMapRequest: ShowPostRequest = ShowPostRequest()

    /** The post Map's popup asks Feed to show. Feed scrolls to it, then clears the request. */
    val showInFeedRequest: ShowPostRequest = ShowPostRequest()

    /**
     * When ViewModels share the state their screens show: while the app is on screen. A rotation,
     * which recreates every screen, doesn't stop their listeners and start them again.
     */
    val whileAppVisible: SharingStarted = WhileAppVisible(
        isAppVisible = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { state -> state.isAtLeast(Lifecycle.State.STARTED) }
    )

    /**
     * Gets ready, on background threads, what the first screens use: the repositories they read,
     * and the map engine. Their first use loads classes, settings and native libraries. Started
     * from `Application.onCreate`, that work runs on other cores while the activity starts,
     * instead of on the main thread while the first frames wait.
     */
    fun prepareFirstScreens() {
        applicationScope.launch {
            postRepository
            profileRepository
            accountRepository
        }
        applicationScope.launch { startMapEngine() }
    }
}
