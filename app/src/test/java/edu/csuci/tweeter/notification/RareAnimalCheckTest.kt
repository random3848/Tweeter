package edu.csuci.tweeter.notification

import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeNumberStore
import edu.csuci.tweeter.data.FakePostLookup
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostCountRarity
import edu.csuci.tweeter.data.PostLookupException
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.location.GeoPoint
import edu.csuci.tweeter.location.LastLocationStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val USER_UID = "maya"
private const val OTHER_UID = "jordan"
private const val RARE_ANIMAL = "Bobcat"
private const val COMMON_ANIMAL = "Western fence lizard"
private const val PLACE = "Library, oak tree"
private const val CHECK_NAME = "rareAnimals"
private const val MAX_POSTS_OF_RARE_ANIMAL = 2
private const val RADIUS_METERS = 10_000.0

private const val FIRST_CHECK_MILLIS = 1_700_000_000_000L
private const val MINUTE_MILLIS = 60_000L
private const val SHARED_MILLIS = FIRST_CHECK_MILLIS + MINUTE_MILLIS
private const val SECOND_CHECK_MILLIS = FIRST_CHECK_MILLIS + 15 * MINUTE_MILLIS
private const val THIRD_CHECK_MILLIS = FIRST_CHECK_MILLIS + 30 * MINUTE_MILLIS

// A hundredth of a degree of latitude is about 1.1 km.
private const val NEARBY_DEGREES = 0.01
private const val FAR_DEGREES = 1.0

private val HOME = GeoPoint(latitude = 34.1626, longitude = -119.0434)

class RareAnimalCheckTest {
    private val accountRepository = FakeAccountRepository(initialUid = USER_UID)
    private val notifier = FakePostNotifier()
    private val numbers = FakeNumberStore()
    private val lastLocationStore = LastLocationStore(numbers)
    private val commonAnimalPosts = List(MAX_POSTS_OF_RARE_ANIMAL + 1) { index ->
        sighting(id = "old-$index", animal = COMMON_ANIMAL, sharedMillis = 0)
    }
    private val postLookup = FakePostLookup(commonAnimalPosts)

    @Test
    fun run_firstTime_notifiesOfNothingSharedBefore() = runTest {
        lastLocationStore.save(HOME)
        share(sighting(id = "rare", sharedMillis = FIRST_CHECK_MILLIS - MINUTE_MILLIS))

        checkAt(FIRST_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_rareAnimalNearbyByOtherUser_notifiesOfItOnce() = runTest {
        startChecking()
        share(sighting(id = "rare"))

        checkAt(SECOND_CHECK_MILLIS).run()

        assertEquals(listOf(rareAnimalNotification("rare")), notifier.notifications)
    }

    @Test
    fun run_postWithoutPlace_saysAnimalIsNearUser() = runTest {
        startChecking()
        share(sighting(id = "rare", place = ""))

        checkAt(SECOND_CHECK_MILLIS).run()

        val text = notifier.notifications.single().text
        assertEquals(NotificationText(R.string.notification_rare_animal_text_no_place), text)
    }

    @Test
    fun run_commonAnimal_notifiesOfNothing() = runTest {
        startChecking()
        share(sighting(id = "common", animal = COMMON_ANIMAL))

        checkAt(SECOND_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_farAway_notifiesOfNothing() = runTest {
        startChecking()
        share(sighting(id = "far", latitude = HOME.latitude + FAR_DEGREES))

        checkAt(SECOND_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_ownPost_notifiesOfNothing() = runTest {
        startChecking()
        share(sighting(id = "mine", authorUid = USER_UID))

        checkAt(SECOND_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_again_doesNotNotifyOfSamePostTwice() = runTest {
        startChecking()
        share(sighting(id = "rare"))
        checkAt(SECOND_CHECK_MILLIS).run()

        checkAt(THIRD_CHECK_MILLIS).run()

        assertEquals(listOf(rareAnimalNotification("rare")), notifier.notifications)
    }

    @Test
    fun run_again_notifiesOfPostSharedSinceLastCheck() = runTest {
        startChecking()
        share(sighting(id = "first"))
        checkAt(SECOND_CHECK_MILLIS).run()
        share(sighting(id = "second", sharedMillis = SECOND_CHECK_MILLIS + MINUTE_MILLIS))

        checkAt(THIRD_CHECK_MILLIS).run()

        val expected = listOf(rareAnimalNotification("first"), rareAnimalNotification("second"))
        assertEquals(expected, notifier.notifications)
    }

    @Test
    fun run_nobodyLoggedIn_neverNotifiesOfPostsSharedMeanwhile() = runTest {
        startChecking()
        accountRepository.logOut()
        share(sighting(id = "rare"))
        checkAt(SECOND_CHECK_MILLIS).run()
        accountRepository.logInAs(USER_UID)

        checkAt(THIRD_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_notificationsNotAllowed_neverNotifiesOfPostsSharedMeanwhile() = runTest {
        startChecking()
        notifier.isAllowed = false
        share(sighting(id = "rare"))
        checkAt(SECOND_CHECK_MILLIS).run()
        notifier.isAllowed = true

        checkAt(THIRD_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_locationUnknown_notifiesOfNothing() = runTest {
        checkAt(FIRST_CHECK_MILLIS).run()
        share(sighting(id = "rare"))

        checkAt(SECOND_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_lookupFails_throwsAndNotifiesOnNextRun() = runTest {
        startChecking()
        share(sighting(id = "rare"))
        postLookup.failure = PostLookupException(IllegalStateException("Offline"))

        val failure = runCatching { checkAt(SECOND_CHECK_MILLIS).run() }.exceptionOrNull()
        postLookup.failure = null
        checkAt(THIRD_CHECK_MILLIS).run()

        assertTrue(failure is PostLookupException)
        assertEquals(listOf(rareAnimalNotification("rare")), notifier.notifications)
    }

    // Knows where the user is, and checks once, so that the next check notifies of what was
    // shared since.
    private suspend fun startChecking() {
        lastLocationStore.save(HOME)
        checkAt(FIRST_CHECK_MILLIS).run()
    }

    private fun share(post: Post) {
        postLookup.posts += post
    }

    private fun checkAt(nowMillis: Long): RareAnimalCheck = RareAnimalCheck(
        accountRepository = accountRepository,
        notifier = notifier,
        lastLocationStore = lastLocationStore,
        lastCheckStore = LastCheckStore(numbers, CHECK_NAME),
        postLookup = postLookup,
        selector = NearbyRareAnimalSelector(
            PostCountRarity(postLookup, maxPosts = MAX_POSTS_OF_RARE_ANIMAL),
            radiusMeters = RADIUS_METERS
        ),
        clock = Clock.fixed(Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC)
    )

    private fun assertNothingNotified() {
        assertEquals(emptyList<PostNotification>(), notifier.notifications)
    }

    private fun rareAnimalNotification(postId: String): PostNotification = PostNotification(
        channel = PostChannel.ANIMALS_NEAR_YOU,
        postId = postId,
        title = NotificationText(R.string.notification_rare_animal_title, RARE_ANIMAL),
        text = NotificationText(R.string.notification_rare_animal_text, PLACE)
    )

    private fun sighting(
        id: String,
        animal: String = RARE_ANIMAL,
        authorUid: String = OTHER_UID,
        place: String = PLACE,
        latitude: Double = HOME.latitude + NEARBY_DEGREES,
        sharedMillis: Long = SHARED_MILLIS
    ): Post = SAMPLE_POSTS.first().copy(
        id = id,
        authorUid = authorUid,
        animalType = animal,
        locationName = place,
        latitude = latitude,
        longitude = HOME.longitude,
        timestamp = Instant.ofEpochMilli(sharedMillis)
    )
}
