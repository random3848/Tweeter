package edu.csuci.tweeter.notification

import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeNumberStore
import edu.csuci.tweeter.data.FakePostLookup
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostLookupException
import edu.csuci.tweeter.data.SAMPLE_POSTS
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

private const val SHARED_MILLIS = 1_700_000_000_000L
private val HOUR_MILLIS = HOURS.toMillis(1)
private val WINDOW_MILLIS = DAYS.toMillis(3)
private val FIRST_CHECK_MILLIS = SHARED_MILLIS + HOUR_MILLIS
private val SECOND_CHECK_MILLIS = FIRST_CHECK_MILLIS + HOUR_MILLIS
private val THIRD_CHECK_MILLIS = SECOND_CHECK_MILLIS + HOUR_MILLIS

// The first check at which a post shared at SHARED_MILLIS is no longer recent.
private val EXPIRY_CHECK_MILLIS = SHARED_MILLIS + WINDOW_MILLIS

class PopularPostCheckTest {
    private val accountRepository = FakeAccountRepository(initialUid = USER_UID)
    private val notifier = FakePostNotifier()
    private val numbers = FakeNumberStore()
    private val postLookup = FakePostLookup()

    @Test
    fun run_popularPostByOtherUser_namesAnimalPlaceAndLikes() = runTest {
        share(post(id = "popular", likeCount = MIN_LIKES + 2))

        checkAt(FIRST_CHECK_MILLIS).run()

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
        share(post(id = "popular", animal = " "))

        checkAt(FIRST_CHECK_MILLIS).run()

        val title = notifier.notifications.single().title
        assertEquals(NotificationText(R.string.notification_popular_post_title), title)
    }

    @Test
    fun run_postWithoutPlace_givesLikesOnly() = runTest {
        share(post(id = "popular", place = ""))

        checkAt(FIRST_CHECK_MILLIS).run()

        val text = notifier.notifications.single().text
        assertEquals(
            NotificationText(R.string.notification_popular_post_text_no_place, "$MIN_LIKES"),
            text
        )
    }

    @Test
    fun run_belowThreshold_notifiesOfNothing() = runTest {
        share(post(id = "almost", likeCount = MIN_LIKES - 1))

        checkAt(FIRST_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_postReachesThresholdLater_notifiesOfItThen() = runTest {
        share(post(id = "rising", likeCount = MIN_LIKES - 1))
        checkAt(FIRST_CHECK_MILLIS).run()
        postLookup.posts = listOf(post(id = "rising", likeCount = MIN_LIKES))

        checkAt(SECOND_CHECK_MILLIS).run()

        assertEquals(listOf("rising"), notifiedPostIds())
    }

    @Test
    fun run_ownPost_notifiesOfNothing() = runTest {
        share(post(id = "mine", authorUid = USER_UID))

        checkAt(FIRST_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_postNoLongerRecent_notifiesOfNothing() = runTest {
        share(post(id = "old"))

        checkAt(EXPIRY_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_againAfterRestart_doesNotNotifyOfSamePostTwice() = runTest {
        share(post(id = "popular"))
        checkAt(FIRST_CHECK_MILLIS).run()

        checkAt(SECOND_CHECK_MILLIS).run()

        assertEquals(listOf("popular"), notifiedPostIds())
    }

    @Test
    fun run_otherUserOnSameDevice_isNotifiedOfSamePost() = runTest {
        share(post(id = "popular"))
        checkAt(FIRST_CHECK_MILLIS).run()
        accountRepository.logInAs(THIRD_UID)

        checkAt(SECOND_CHECK_MILLIS).run()

        assertEquals(listOf("popular", "popular"), notifiedPostIds())
    }

    @Test
    fun run_likesDropBelowThresholdAndReturn_doesNotNotifyTwice() = runTest {
        share(post(id = "popular"))
        checkAt(FIRST_CHECK_MILLIS).run()
        postLookup.posts = listOf(post(id = "popular", likeCount = MIN_LIKES - 1))
        checkAt(SECOND_CHECK_MILLIS).run()
        postLookup.posts = listOf(post(id = "popular", likeCount = MIN_LIKES))

        checkAt(THIRD_CHECK_MILLIS).run()

        assertEquals(listOf("popular"), notifiedPostIds())
    }

    @Test
    fun run_again_notifiesOfPostPopularSinceLastCheck() = runTest {
        share(post(id = "first"))
        checkAt(FIRST_CHECK_MILLIS).run()
        share(post(id = "second"))

        checkAt(SECOND_CHECK_MILLIS).run()

        assertEquals(listOf("first", "second"), notifiedPostIds())
    }

    @Test
    fun run_postLeavesWindow_forgetsIt() = runTest {
        share(post(id = "popular"))
        checkAt(FIRST_CHECK_MILLIS).run()

        checkAt(EXPIRY_CHECK_MILLIS).run()

        val notifiedPosts = NotifiedPostStore(numbers, CHECK_NAME, USER_UID).notifiedPostIds()
        assertEquals(emptySet<String>(), notifiedPosts)
    }

    @Test
    fun run_nobodyLoggedIn_doesNotLookUpPosts() = runTest {
        accountRepository.logOut()
        share(post(id = "popular"))
        postLookup.failure = PostLookupException(IllegalStateException("Not signed in"))

        checkAt(FIRST_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_userLogsInWhilePostIsRecent_notifiesOfItThen() = runTest {
        accountRepository.logOut()
        share(post(id = "popular"))
        checkAt(FIRST_CHECK_MILLIS).run()
        accountRepository.logInAs(USER_UID)

        checkAt(SECOND_CHECK_MILLIS).run()

        assertEquals(listOf("popular"), notifiedPostIds())
    }

    @Test
    fun run_notificationsNotAllowed_doesNotLookUpPosts() = runTest {
        notifier.isAllowed = false
        share(post(id = "popular"))
        postLookup.failure = PostLookupException(IllegalStateException("Offline"))

        checkAt(FIRST_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_popularChannelTurnedOff_doesNotLookUpPosts() = runTest {
        notifier.turnedOffChannels += PostChannel.POPULAR_POSTS
        share(post(id = "popular"))
        postLookup.failure = PostLookupException(IllegalStateException("Offline"))

        checkAt(FIRST_CHECK_MILLIS).run()

        assertNothingNotified()
    }

    @Test
    fun run_otherChannelTurnedOff_stillNotifies() = runTest {
        notifier.turnedOffChannels += PostChannel.ANIMALS_NEAR_YOU
        share(post(id = "popular"))

        checkAt(FIRST_CHECK_MILLIS).run()

        assertEquals(listOf("popular"), notifiedPostIds())
    }

    @Test
    fun run_lookupFails_throwsAndNotifiesOnNextRun() = runTest {
        share(post(id = "popular"))
        postLookup.failure = PostLookupException(IllegalStateException("Offline"))

        val failure = runCatching { checkAt(FIRST_CHECK_MILLIS).run() }.exceptionOrNull()
        postLookup.failure = null
        checkAt(SECOND_CHECK_MILLIS).run()

        assertTrue(failure is PostLookupException)
        assertEquals(listOf("popular"), notifiedPostIds())
    }

    private fun share(post: Post) {
        postLookup.posts += post
    }

    // A new check, with its own stores over the same saved numbers, as after the app restarts.
    private fun checkAt(nowMillis: Long): PopularPostCheck = PopularPostCheck(
        accountRepository = accountRepository,
        notifier = notifier,
        notifiedPostStores = { uid -> NotifiedPostStore(numbers, CHECK_NAME, uid) },
        postLookup = postLookup,
        selector = PopularPostSelector(minLikeCount = MIN_LIKES, windowMillis = WINDOW_MILLIS),
        clock = Clock.fixed(Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC)
    )

    private fun notifiedPostIds(): List<String> = notifier.notifications.map { it.postId }

    private fun assertNothingNotified() {
        assertEquals(emptyList<PostNotification>(), notifier.notifications)
    }

    private fun post(
        id: String,
        likeCount: Int = MIN_LIKES,
        animal: String = ANIMAL,
        place: String = PLACE,
        authorUid: String = OTHER_UID
    ): Post = SAMPLE_POSTS.first().copy(
        id = id,
        authorUid = authorUid,
        animalType = animal,
        locationName = place,
        likeCount = likeCount,
        timestamp = Instant.ofEpochMilli(SHARED_MILLIS)
    )
}
