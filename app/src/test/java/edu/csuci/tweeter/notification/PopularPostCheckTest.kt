package edu.csuci.tweeter.notification

import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeNumberStore
import edu.csuci.tweeter.data.FakePostLookup
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostLookupException
import edu.csuci.tweeter.data.SAMPLE_POSTS
import java.time.Instant
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.HOURS
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val USER_UID = "maya"
private const val OTHER_UID = "jordan"
private const val THIRD_UID = "sam"
private const val ANIMAL = "Bobcat"
private const val PLACE = "Library, oak tree"
private const val CHECK_NAME = "popularPosts"
private const val MIN_LIKES = 10
private const val POSTS_PER_PAGE = 2

// November 2023 by the server's clock, so the clock of any device running these tests is years
// ahead of the server's.
private const val SHARED_MILLIS = 1_700_000_000_000L

// The year 2200 by the server's clock, so the clock of any device running these tests is far
// behind the server's.
private const val FUTURE_SHARED_MILLIS = 7_258_118_400_000L

private val HOUR_MILLIS = HOURS.toMillis(1)
private val WINDOW_MILLIS = DAYS.toMillis(3)

class PopularPostCheckTest {
    private val accountRepository = FakeAccountRepository(initialUid = USER_UID)
    private val notifier = FakePostNotifier()
    private val numbers = FakeNumberStore()
    private val postLookup = FakePostLookup()

    // A new check for each run, with its own stores over the same saved numbers, would act the
    // same: it keeps nothing in memory between runs.
    private val check = PopularPostCheck(
        accountRepository = accountRepository,
        notifier = notifier,
        lastCheckStores = { uid -> LastCheckStore(numbers, "$CHECK_NAME.$uid") },
        notifiedPostStores = { uid -> NotifiedPostStore(numbers, CHECK_NAME, uid) },
        postLookup = postLookup,
        selector = PopularPostSelector(minLikeCount = MIN_LIKES, windowMillis = WINDOW_MILLIS)
    )

    @Test
    fun run_popularPostByOtherUser_namesAnimalPlaceAndLikes() = runTest {
        startChecking()
        share(post(id = "popular", likeCount = MIN_LIKES + 2))

        check.run()

        val expected = PostNotification(
            channel = PostChannel.POPULAR_POSTS,
            postId = "popular",
            title = NotificationText(R.string.notification_popular_post_title_animal, ANIMAL),
            text = NotificationText(R.string.notification_popular_post_text, "12", PLACE)
        )
        assertEquals(listOf(expected), notifier.notifications)
    }

    @Test
    fun run_postWithoutAnimal_saysPostIsPopular() = runTest {
        startChecking()
        share(post(id = "popular", animal = " "))

        check.run()

        val title = notifier.notifications.single().title
        assertEquals(NotificationText(R.string.notification_popular_post_title), title)
    }

    @Test
    fun run_postWithoutPlace_givesLikesOnly() = runTest {
        startChecking()
        share(post(id = "popular", place = ""))

        check.run()

        val text = notifier.notifications.single().text
        assertEquals(
            NotificationText(R.string.notification_popular_post_text_no_place, "$MIN_LIKES"),
            text
        )
    }

    @Test
    fun run_noPostsOnServer_notifiesOfNothing() = runTest {
        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_readsOnlyPostsAtOrAboveThreshold() = runTest {
        share(post(id = "almost", likeCount = MIN_LIKES - 1))
        share(post(id = "popular", likeCount = MIN_LIKES))

        check.run()

        assertEquals(listOf("popular"), postLookup.readPostIds)
    }

    @Test
    fun run_firstRunForUser_notifiesOfNothingPopularAlready() = runTest {
        share(post(id = "popular"))

        check.run()
        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_firstRunForUser_thenNotifiesOfPostsPopularSince() = runTest {
        share(post(id = "before"))
        check.run()
        share(post(id = "after"))

        check.run()

        assertEquals(listOf("after"), notifiedPostIds())
    }

    @Test
    fun run_postPopularBeforeFirstRunWithLookupFailure_neverNotifiedOf() = runTest {
        share(post(id = "popular"))
        postLookup.failure = PostLookupException(IllegalStateException("Offline"))
        runCatching { check.run() }
        postLookup.failure = null

        check.run()
        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_newUserLogsIn_notifiesThemOfNothingPopularAlready() = runTest {
        startChecking()
        share(post(id = "popular"))
        accountRepository.logInAs(THIRD_UID)

        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_channelTurnedBackOn_notifiesOfNothingPopularMeanwhile() = runTest {
        startChecking()
        notifier.turnedOffChannels += PostChannel.POPULAR_POSTS
        check.run()
        share(post(id = "popular"))
        notifier.turnedOffChannels.clear()

        check.run()
        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_notificationsAllowedAgain_notifiesOfNothingPopularMeanwhile() = runTest {
        startChecking()
        notifier.isAllowed = false
        check.run()
        share(post(id = "popular"))
        notifier.isAllowed = true

        check.run()
        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_userLogsBackInAfterWindow_notifiesOfNothingPopularMeanwhile() = runTest {
        startChecking()
        accountRepository.logOut()
        check.run()
        share(post(id = "popular", sharedMillis = SHARED_MILLIS + WINDOW_MILLIS))
        accountRepository.logInAs(USER_UID)

        check.run()
        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_userLogsBackInWithinWindow_notifiesOfPostPopularMeanwhile() = runTest {
        startChecking()
        accountRepository.logOut()
        check.run()
        share(post(id = "popular"))
        accountRepository.logInAs(USER_UID)

        check.run()

        assertEquals(listOf("popular"), notifiedPostIds())
    }

    @Test
    fun run_belowThreshold_notifiesOfNothing() = runTest {
        startChecking()
        share(post(id = "almost", likeCount = MIN_LIKES - 1))

        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_postReachesThresholdLater_notifiesOfItThen() = runTest {
        startChecking()
        share(post(id = "rising", likeCount = MIN_LIKES - 1))
        check.run()
        postLookup.posts = listOf(post(id = "rising", likeCount = MIN_LIKES))

        check.run()

        assertEquals(listOf("rising"), notifiedPostIds())
    }

    @Test
    fun run_ownPost_notifiesOfNothing() = runTest {
        startChecking()
        share(post(id = "mine", authorUid = USER_UID))

        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_postJustInsideWindowOfNewestPost_notifiesOfIt() = runTest {
        checkAt(SHARED_MILLIS)
        share(post(id = "popular"))
        serverTimeIs(SHARED_MILLIS + WINDOW_MILLIS - 1)

        check.run()

        assertEquals(listOf("popular"), notifiedPostIds())
    }

    @Test
    fun run_postWindowOlderThanNewestPost_notifiesOfNothing() = runTest {
        checkAt(SHARED_MILLIS + WINDOW_MILLIS - HOUR_MILLIS)
        share(post(id = "old"))
        serverTimeIs(SHARED_MILLIS + WINDOW_MILLIS)

        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_deviceClockAheadOfServer_measuresWindowFromServer() = runTest {
        startChecking()
        share(post(id = "popular"))
        serverTimeIs(SHARED_MILLIS + HOUR_MILLIS)

        check.run()

        assertEquals(listOf("popular"), notifiedPostIds())
    }

    @Test
    fun run_deviceClockBehindServer_measuresWindowFromServer() = runTest {
        checkAt(FUTURE_SHARED_MILLIS + WINDOW_MILLIS - HOUR_MILLIS)
        share(post(id = "old", sharedMillis = FUTURE_SHARED_MILLIS))
        serverTimeIs(FUTURE_SHARED_MILLIS + WINDOW_MILLIS)

        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_morePopularPostsThanOnePage_notifiesOfLeastLikedToo() = runTest {
        startChecking()
        postLookup.pageSize = POSTS_PER_PAGE
        share(post(id = "least-liked"))
        repeat(POSTS_PER_PAGE) { index ->
            share(post(id = "more-liked-$index", likeCount = MIN_LIKES + index + 1))
        }

        check.run()

        assertTrue("least-liked" in notifiedPostIds())
    }

    @Test
    fun run_again_doesNotNotifyOfSamePostTwice() = runTest {
        startChecking()
        share(post(id = "popular"))
        check.run()
        serverTimeIs(SHARED_MILLIS + HOUR_MILLIS)

        check.run()

        assertEquals(listOf("popular"), notifiedPostIds())
    }

    @Test
    fun run_otherUserOnSameDevice_isNotifiedOfSamePost() = runTest {
        startChecking()
        accountRepository.logInAs(THIRD_UID)
        check.run()
        accountRepository.logInAs(USER_UID)
        share(post(id = "popular"))
        check.run()
        accountRepository.logInAs(THIRD_UID)

        check.run()

        assertEquals(listOf("popular", "popular"), notifiedPostIds())
    }

    @Test
    fun run_likesDropBelowThresholdAndReturn_doesNotNotifyTwice() = runTest {
        startChecking()
        share(post(id = "popular"))
        check.run()
        postLookup.posts = listOf(post(id = "popular", likeCount = MIN_LIKES - 1))
        check.run()
        postLookup.posts = listOf(post(id = "popular", likeCount = MIN_LIKES))

        check.run()

        assertEquals(listOf("popular"), notifiedPostIds())
    }

    @Test
    fun run_again_notifiesOfPostPopularSinceLastCheck() = runTest {
        startChecking()
        share(post(id = "first"))
        check.run()
        share(post(id = "second"))

        check.run()

        assertEquals(listOf("first", "second"), notifiedPostIds())
    }

    @Test
    fun run_postLeavesWindowOfNewestPost_forgetsIt() = runTest {
        startChecking()
        share(post(id = "popular"))
        check.run()
        serverTimeIs(SHARED_MILLIS + WINDOW_MILLIS)

        check.run()

        val notifiedPosts = NotifiedPostStore(numbers, CHECK_NAME, USER_UID).notifiedPostIds()
        assertEquals(emptySet<String>(), notifiedPosts)
    }

    @Test
    fun run_nobodyLoggedIn_doesNotLookUpPosts() = runTest {
        accountRepository.logOut()
        share(post(id = "popular"))
        postLookup.failure = PostLookupException(IllegalStateException("Not signed in"))

        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_notificationsNotAllowed_doesNotLookUpPosts() = runTest {
        notifier.isAllowed = false
        share(post(id = "popular"))
        postLookup.failure = PostLookupException(IllegalStateException("Offline"))

        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_popularChannelTurnedOff_doesNotLookUpPosts() = runTest {
        notifier.turnedOffChannels += PostChannel.POPULAR_POSTS
        share(post(id = "popular"))
        postLookup.failure = PostLookupException(IllegalStateException("Offline"))

        check.run()

        assertNothingNotified()
    }

    @Test
    fun run_otherChannelTurnedOff_stillNotifies() = runTest {
        startChecking()
        notifier.turnedOffChannels += PostChannel.ANIMALS_NEAR_YOU
        share(post(id = "popular"))

        check.run()

        assertEquals(listOf("popular"), notifiedPostIds())
    }

    @Test
    fun run_lookupFails_throwsAndNotifiesOnNextRun() = runTest {
        startChecking()
        share(post(id = "popular"))
        postLookup.failure = PostLookupException(IllegalStateException("Offline"))

        val failure = runCatching { check.run() }.exceptionOrNull()
        postLookup.failure = null
        check.run()

        assertTrue(failure is PostLookupException)
        assertEquals(listOf("popular"), notifiedPostIds())
    }

    // Checks once, an hour before the posts the test shares, so that the check has started for
    // the user and the next check notifies of what became popular since.
    private suspend fun startChecking() {
        checkAt(SHARED_MILLIS - HOUR_MILLIS)
    }

    private suspend fun checkAt(serverMillis: Long) {
        serverTimeIs(serverMillis)
        check.run()
    }

    private fun share(post: Post) {
        postLookup.posts += post
    }

    // Makes the server's newest post one received at [millis], with no likes, which is how the
    // check tells the time.
    private fun serverTimeIs(millis: Long) {
        share(post(id = "newest-$millis", likeCount = 0, sharedMillis = millis))
    }

    private fun notifiedPostIds(): List<String> = notifier.notifications.map { it.postId }

    private fun assertNothingNotified() {
        assertEquals(emptyList<PostNotification>(), notifier.notifications)
    }

    private fun post(
        id: String,
        likeCount: Int = MIN_LIKES,
        animal: String = ANIMAL,
        place: String = PLACE,
        authorUid: String = OTHER_UID,
        sharedMillis: Long = SHARED_MILLIS
    ): Post = SAMPLE_POSTS.first().copy(
        id = id,
        authorUid = authorUid,
        animalType = animal,
        locationName = place,
        likeCount = likeCount,
        timestamp = Instant.ofEpochMilli(sharedMillis)
    )
}
