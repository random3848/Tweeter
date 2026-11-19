package edu.csuci.tweeter

import android.content.Context
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.AnimalRepository
import edu.csuci.tweeter.data.BookmarkRepository
import edu.csuci.tweeter.data.CameraFileStore
import edu.csuci.tweeter.data.CommentRepository
import edu.csuci.tweeter.data.FakeAnimalRepository
import edu.csuci.tweeter.data.FirebaseAccountRepository
import edu.csuci.tweeter.data.FirebasePhotoRepository
import edu.csuci.tweeter.data.FirestoreBookmarkRepository
import edu.csuci.tweeter.data.FirestoreCommentRepository
import edu.csuci.tweeter.data.FirestoreFollowRepository
import edu.csuci.tweeter.data.FirestoreLikeRepository
import edu.csuci.tweeter.data.FirestorePostLookup
import edu.csuci.tweeter.data.FirestorePostRepository
import edu.csuci.tweeter.data.FirestoreProfileRepository
import edu.csuci.tweeter.data.FirestoreReportRepository
import edu.csuci.tweeter.data.FollowRepository
import edu.csuci.tweeter.data.GuestSignIn
import edu.csuci.tweeter.data.LikeRepository
import edu.csuci.tweeter.data.NumberStore
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PostCountRarity
import edu.csuci.tweeter.data.PostDeleter
import edu.csuci.tweeter.data.PostLookup
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.ProfileSaver
import edu.csuci.tweeter.data.ReportRepository
import edu.csuci.tweeter.data.SharedPreferencesNumberStore
import edu.csuci.tweeter.location.DeviceLocationProvider
import edu.csuci.tweeter.location.LastLocationStore
import edu.csuci.tweeter.location.LocationTagRemover
import edu.csuci.tweeter.notification.LastCheckStore
import edu.csuci.tweeter.notification.NearbyRareAnimalSelector
import edu.csuci.tweeter.notification.NotificationPermissionRecord
import edu.csuci.tweeter.notification.NotifiedPostStore
import edu.csuci.tweeter.notification.PopularPostCheck
import edu.csuci.tweeter.notification.PopularPostCheckWorker
import edu.csuci.tweeter.notification.PopularPostSelector
import edu.csuci.tweeter.notification.PostCheckWorkerFactory
import edu.csuci.tweeter.notification.PostNotifier
import edu.csuci.tweeter.notification.RareAnimalCheck
import edu.csuci.tweeter.notification.RareAnimalCheckWorker
import edu.csuci.tweeter.notification.SystemPostNotifier
import edu.csuci.tweeter.notification.schedulePostCheck
import edu.csuci.tweeter.ui.map.startMapEngine
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

// How long a ViewModel keeps sharing after its screen stops collecting: longer than an activity
// takes to be recreated for a rotation.
private const val SHARING_STOP_TIMEOUT_MILLIS = 5_000L

// The shared preferences file of what the app remembers on the device between runs.
private const val SAVED_NUMBERS_FILE = "saved_numbers"

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
 *             postsDispatcher = Dispatchers.Default,
 *             sharing = application.container.whileScreenCollects
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

    // Signs guests in for the post and account repositories, one sign-in at a time between them.
    private val guestSignIn: GuestSignIn by lazy {
        GuestSignIn.to(FirebaseAuth.getInstance(), scope = applicationScope)
    }

    /**
     * Every post in the app, stored in Firestore. Its post table is one listener shared by every
     * screen, parsed on [Dispatchers.Default].
     */
    val postRepository: PostRepository by lazy {
        FirestorePostRepository(
            guestSignIn = guestSignIn,
            firestore = FirebaseFirestore.getInstance(),
            scope = applicationScope,
            dispatcher = Dispatchers.Default
        )
    }

    /**
     * Every user's profile, stored in Firestore. Search's list of every profile signs a guest in
     * first if nobody is, one sign-in at a time with the post repository's, and is parsed on
     * [Dispatchers.Default].
     */
    val profileRepository: ProfileRepository by lazy {
        FirestoreProfileRepository(
            guestSignIn = guestSignIn,
            firestore = FirebaseFirestore.getInstance(),
            dispatcher = Dispatchers.Default
        )
    }

    /** Reads and changes follower relationships in Firestore. */
    val followRepository: FollowRepository by lazy {
        FirestoreFollowRepository(
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance()
        )
    }

    /** Every comment on posts, stored in Firestore. */
    val commentRepository: CommentRepository by lazy {
        FirestoreCommentRepository(firestore = FirebaseFirestore.getInstance())
    }

    /** Reports of posts and users, stored in Firestore for the team to review. */
    val reportRepository: ReportRepository by lazy {
        FirestoreReportRepository(firestore = FirebaseFirestore.getInstance())
    }

    /**
     * Shared authentication operations and the registered user's identity.
     */
    val accountRepository: AccountRepository by lazy {
        FirebaseAccountRepository(auth = FirebaseAuth.getInstance(), guestSignIn = guestSignIn)
    }

    /**
     * The logged-in user's likes of posts, stored in Firestore with the posts' like counts, and
     * read on [Dispatchers.Default].
     */
    val likeRepository: LikeRepository by lazy {
        FirestoreLikeRepository(
            accountRepository = accountRepository,
            guestSignIn = guestSignIn,
            firestore = FirebaseFirestore.getInstance(),
            dispatcher = Dispatchers.Default
        )
    }

    /**
     * The logged-in user's saved posts, stored in Firestore under their profile, and read on
     * [Dispatchers.Default].
     */
    val bookmarkRepository: BookmarkRepository by lazy {
        FirestoreBookmarkRepository(
            accountRepository = accountRepository,
            guestSignIn = guestSignIn,
            firestore = FirebaseFirestore.getInstance(),
            dispatcher = Dispatchers.Default
        )
    }

    /** Uploads post and profile photos to Firebase Storage. */
    val photoRepository: PhotoRepository by lazy {
        FirebasePhotoRepository(
            auth = FirebaseAuth.getInstance(),
            storage = FirebaseStorage.getInstance(),
            locationTagRemover = LocationTagRemover(
                context,
                scope = applicationScope,
                ioDispatcher = Dispatchers.IO
            )
        )
    }

    /**
     * The animals a post can be tagged with: a fixed list in memory until the iNaturalist
     * repository is built; that work replaces this.
     */
    val animalRepository: AnimalRepository = FakeAnimalRepository()

    /**
     * Saves profiles and deletes the photos the saves leave unused, waiting for the server in
     * the app's scope, so leaving Edit profile doesn't stop that.
     */
    val profileSaver: ProfileSaver by lazy {
        ProfileSaver(profileRepository, photoRepository, scope = applicationScope)
    }

    /**
     * Deletes the logged-in user's posts and their photos in the app's scope, so leaving Feed or
     * Profile doesn't stop that.
     */
    val postDeleter: PostDeleter by lazy {
        PostDeleter(
            postRepository = postRepository,
            photoRepository = photoRepository,
            accountRepository = accountRepository,
            scope = applicationScope
        )
    }

    /**
     * The files the camera app saves post and profile photos into. Discarded ones are deleted in
     * the app's scope, so leaving a screen doesn't stop that.
     */
    val cameraFileStore: CameraFileStore by lazy {
        CameraFileStore.inCacheOf(context, scope = applicationScope, ioDispatcher = Dispatchers.IO)
    }

    // What the app remembers on the device between runs, such as where the device last was.
    private val savedNumbers: NumberStore by lazy {
        SharedPreferencesNumberStore(
            context,
            name = SAVED_NUMBERS_FILE,
            ioDispatcher = Dispatchers.IO
        )
    }

    // Where the device last was, which notifications of posts near the user measure from.
    private val lastLocationStore: LastLocationStore by lazy { LastLocationStore(savedNumbers) }

    /** Location fixes, each saved as where the device last was. */
    val deviceLocationProvider: DeviceLocationProvider by lazy {
        DeviceLocationProvider(context, lastLocationStore)
    }

    // Shows notifications about posts in the system's notification shade, and creates their
    // channels when the app starts.
    private val systemPostNotifier: SystemPostNotifier by lazy { SystemPostNotifier(context) }

    /** Shows notifications about posts in the system's notification shade. */
    val postNotifier: PostNotifier
        get() = systemPostNotifier

    /** Whether the app has asked the user for permission to show notifications. */
    val notificationPermissionRecord: NotificationPermissionRecord by lazy {
        NotificationPermissionRecord(savedNumbers)
    }

    /**
     * Looks posts up on the server: for the checks for posts to notify the user of, and for Feed,
     * to tell whether a post it was asked to show and can't find was deleted.
     */
    val postLookup: PostLookup by lazy {
        FirestorePostLookup(FirebaseFirestore.getInstance(), dispatcher = Dispatchers.Default)
    }

    // Checks for rare animals posted near the logged-in user, judging rarity by the app's own
    // posts until the iNaturalist repository is built; that work replaces PostCountRarity here.
    private val rareAnimalCheck: RareAnimalCheck by lazy {
        RareAnimalCheck(
            accountRepository = accountRepository,
            notifier = postNotifier,
            lastLocationStore = lastLocationStore,
            lastCheckStore = LastCheckStore(savedNumbers, checkName = RareAnimalCheckWorker.NAME),
            notifiedPostStores = notifiedPostStoresOf(RareAnimalCheckWorker.NAME),
            postLookup = postLookup,
            selector = NearbyRareAnimalSelector(PostCountRarity(postLookup))
        )
    }

    // Gets the posts the check named [checkName] notified each user of the device of.
    private fun notifiedPostStoresOf(checkName: String): (uid: String) -> NotifiedPostStore =
        { uid -> NotifiedPostStore(savedNumbers, checkName, uid) }

    // Checks for recent posts by others that became popular.
    private val popularPostCheck: PopularPostCheck by lazy {
        PopularPostCheck(
            accountRepository = accountRepository,
            notifier = postNotifier,
            notifiedPostStores = notifiedPostStoresOf(PopularPostCheckWorker.NAME),
            postLookup = postLookup,
            selector = PopularPostSelector()
        )
    }

    /**
     * Creates the workers of the checks for posts to notify the user of, for WorkManager's
     * configuration in [TweeterApplication].
     */
    val workerFactory: WorkerFactory by lazy {
        PostCheckWorkerFactory(
            rareAnimalCheck = { rareAnimalCheck },
            popularPostCheck = { popularPostCheck }
        )
    }

    /** The post Feed asks Map to show. Map opens the post's popup, then clears the request. */
    val showOnMapRequest: ShowPostRequest = ShowPostRequest()

    /**
     * The post Map's popup, or a tapped notification, asks Feed to show. Feed scrolls to it, then
     * clears the request.
     */
    val showInFeedRequest: ShowPostRequest = ShowPostRequest()

    /**
     * When ViewModels share the state their screens show: while a screen collects it, and for 5 s
     * after the last collector stops. That covers an activity recreated for a rotation, which
     * doesn't stop the listeners and start them again, while the ViewModel of a screen that isn't
     * shown, such as a saved back-stack entry (Feed's while Map is open), stops 5 s later.
     */
    val whileScreenCollects: SharingStarted =
        SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARING_STOP_TIMEOUT_MILLIS)

    /**
     * Gets ready, on background threads, what the first screens use: the repositories they read,
     * and the map engine, so that opening Map doesn't load it on the main thread. Their first use
     * loads classes, settings and native libraries. Started from `MainActivity.onCreate`, that
     * work runs on other cores while the activity starts, instead of on the main thread while the
     * first frames wait; a process WorkManager starts for a check, which shows nothing, skips it.
     */
    fun prepareFirstScreens() {
        applicationScope.launch {
            postRepository
            profileRepository
            accountRepository
        }
        applicationScope.launch { startMapEngine() }
    }

    /**
     * Schedules the checks for posts to notify the user of, which then run every so often, also
     * while the app is closed. Started from `Application.onCreate`, it runs on a background
     * thread, since WorkManager's first use opens its database.
     */
    fun schedulePostChecks() {
        applicationScope.launch {
            val workManager = WorkManager.getInstance(context)
            workManager.schedulePostCheck(
                RareAnimalCheckWorker.NAME,
                RareAnimalCheckWorker::class.java
            )
            workManager.schedulePostCheck(
                PopularPostCheckWorker.NAME,
                PopularPostCheckWorker::class.java,
                intervalMinutes = PopularPostCheckWorker.INTERVAL_MINUTES
            )
        }
    }

    /**
     * Creates the channels of the notifications about posts, so that users can turn each kind off
     * in the system's settings for the app before its first notification. Started from
     * `Application.onCreate`, it runs on a background thread, since it calls the system.
     */
    fun createPostNotificationChannels() {
        applicationScope.launch { systemPostNotifier.createChannels() }
    }
}
