@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.map

import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeFollowRepository
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Position

private const val UNKNOWN_POST_ID = "no-such-post"

private const val ONLY_ROW = 0

// The logged-in user, a follower of theirs whom they don't follow back, and a user they follow.
private const val USER = "user"
private const val FOLLOWER = "follower"
private const val FOLLOWED = "followed"

// Another user, who logs in on the same phone.
private const val OTHER_USER = "other user"

class MapViewModelTest {
    private val showOnMapRequest = ShowPostRequest()
    private val showInFeedRequest = ShowPostRequest()
    private val accountRepository = FakeAccountRepository(initialUid = USER)
    private val followRepository = FakeFollowRepository()

    private val usersPost = SAMPLE_POSTS[0].copy(id = "user's", authorUid = USER)
    private val followersPost = SAMPLE_POSTS[1].copy(id = "follower's", authorUid = FOLLOWER)
    private val followedUsersPost = SAMPLE_POSTS[2].copy(id = "followed's", authorUid = FOLLOWED)
    private val postsByEach = listOf(usersPost, followersPost, followedUsersPost)

    @Before
    fun useTestMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectPost_opensPopupAndMovesToPin() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS[1]

        viewModel.selectPost(post.id)

        assertEquals(
            SAMPLE_POSTS.indexOf(post) to MapMove.ToPost(post.id),
            viewModel.uiState.value.selection()
        )
    }

    @Test
    fun selectPost_unknownPost_opensNoPopup() = runTest {
        val viewModel = mapViewModel()

        viewModel.selectPost(UNKNOWN_POST_ID)

        assertNull(viewModel.uiState.value.selectedRow)
    }

    @Test
    fun selectPost_postNotLoadedYet_opensPopupOnceItLoads() = runTest {
        val repository = FakePostRepository(initialPosts = emptyList())
        val viewModel = mapViewModel(repository)
        val post = SAMPLE_POSTS.first()
        viewModel.selectPost(post.id)

        repository.addPost(post)

        assertEquals(ONLY_ROW, viewModel.uiState.value.selectedRow)
    }

    @Test
    fun clearSelection_closesPopupAndDropsMoveToIt() = runTest {
        val viewModel = mapViewModel()
        viewModel.selectPost(SAMPLE_POSTS.first().id)

        viewModel.clearSelection()

        assertEquals(null to null, viewModel.uiState.value.selection())
    }

    @Test
    fun clearSelection_keepsMoveToUser() = runTest {
        val viewModel = mapViewModel()
        viewModel.moveToUser()

        viewModel.clearSelection()

        assertEquals(MapMove.ToUser, viewModel.uiState.value.pendingMove)
    }

    @Test
    fun finishMove_keepsPopupOpenWithoutMovingAgain() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS.first()
        viewModel.selectPost(post.id)

        viewModel.finishMove(MapMove.ToPost(post.id))

        assertEquals(SAMPLE_POSTS.indexOf(post) to null, viewModel.uiState.value.selection())
    }

    @Test
    fun finishMove_toEarlierPost_keepsMoveToPostSelectedSince() = runTest {
        val viewModel = mapViewModel()
        val earlier = SAMPLE_POSTS[0]
        val later = SAMPLE_POSTS[1]
        viewModel.selectPost(earlier.id)
        viewModel.selectPost(later.id)

        viewModel.finishMove(MapMove.ToPost(earlier.id))

        assertEquals(MapMove.ToPost(later.id), viewModel.uiState.value.pendingMove)
    }

    @Test
    fun moveToUser_replacesMoveToPost() = runTest {
        val viewModel = mapViewModel()
        viewModel.selectPost(SAMPLE_POSTS.first().id)

        viewModel.moveToUser()

        assertEquals(MapMove.ToUser, viewModel.uiState.value.pendingMove)
    }

    @Test
    fun selectPost_whileWaitingForUsersLocation_movesToPostInstead() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS.first()
        viewModel.moveToUser()

        viewModel.selectPost(post.id)

        assertEquals(MapMove.ToPost(post.id), viewModel.uiState.value.pendingMove)
    }

    @Test
    fun showOnMapRequest_sentBeforeMapOpens_opensPostsPopup() = runTest {
        val post = SAMPLE_POSTS[1]
        showOnMapRequest.send(post.id)

        val viewModel = mapViewModel()

        assertEquals(
            SAMPLE_POSTS.indexOf(post) to MapMove.ToPost(post.id),
            viewModel.uiState.value.selection()
        )
    }

    @Test
    fun showOnMapRequest_sentWhileMapIsOpen_opensPostsPopup() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS[1]

        showOnMapRequest.send(post.id)

        assertEquals(SAMPLE_POSTS.indexOf(post), viewModel.uiState.value.selectedRow)
    }

    @Test
    fun showOnMapRequest_isHandledOnlyOnce() = runTest {
        showOnMapRequest.send(SAMPLE_POSTS[1].id)
        mapViewModel()

        val reopenedMap = mapViewModel()

        assertEquals(null to null, reopenedMap.uiState.value.selection())
    }

    @Test
    fun showInFeed_asksFeedToShowPost() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS[1]

        viewModel.showInFeed(post.id)

        assertEquals(post.id, showInFeedRequest.postId.value)
    }

    @Test
    fun uiState_postAdded_pinsIt() = runTest {
        val repository = FakePostRepository(initialPosts = emptyList())
        val viewModel = mapViewModel(repository)
        val post = SAMPLE_POSTS.first()

        repository.addPost(post)

        assertEquals(listOf(post.id), viewModel.uiState.value.pinnedPostIds())
    }

    @Test
    fun uiState_mapHidden_doesNotFollowPosts() = runTest {
        val repository = FakePostRepository(initialPosts = emptyList())
        val viewModel = mapViewModel(repository)
        viewModel.setMapShown(false)

        repository.addPost(SAMPLE_POSTS.first())

        assertEquals(0, viewModel.uiState.value.posts.size)
    }

    @Test
    fun uiState_mapShownAgain_followsPosts() = runTest {
        val repository = FakePostRepository(initialPosts = emptyList())
        val viewModel = mapViewModel(repository)
        viewModel.setMapShown(false)
        repository.addPost(SAMPLE_POSTS.first())

        viewModel.setMapShown(true)

        assertEquals(1, viewModel.uiState.value.posts.size)
    }

    @Test
    fun uiState_mapShownAgainWithSamePosts_keepsTheirPins() = runTest {
        val viewModel = mapViewModel()
        val pinnedPosts = viewModel.uiState.value.pinnedPosts
        viewModel.setMapShown(false)

        viewModel.setMapShown(true)

        assertSame(pinnedPosts, viewModel.uiState.value.pinnedPosts)
    }

    @Test
    fun selectPost_pinsSelectedPostAlone() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS[1]

        viewModel.selectPost(post.id)

        val state = viewModel.uiState.value
        assertEquals(
            GeoJsonData.JsonString(pinJsonOf(state.posts, SAMPLE_POSTS.indexOf(post))),
            state.selectedPin
        )
    }

    @Test
    fun selectPost_movesToWherePhotoWasTaken() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS[1]

        viewModel.selectPost(post.id)

        assertEquals(
            Position(longitude = post.longitude, latitude = post.latitude),
            viewModel.uiState.value.selectedPosition
        )
    }

    @Test
    fun selectPost_latitudeIsNan_hasNoPositionToMoveTo() = runTest {
        val post = SAMPLE_POSTS.first().copy(latitude = Double.NaN)
        val viewModel = mapViewModel(FakePostRepository(initialPosts = listOf(post)))

        viewModel.selectPost(post.id)

        assertNull(viewModel.uiState.value.selectedPosition)
    }

    @Test
    fun selectPost_longitudeInfinite_hasNoPositionToMoveTo() = runTest {
        val post = SAMPLE_POSTS.first().copy(longitude = Double.POSITIVE_INFINITY)
        val viewModel = mapViewModel(FakePostRepository(initialPosts = listOf(post)))

        viewModel.selectPost(post.id)

        assertNull(viewModel.uiState.value.selectedPosition)
    }

    @Test
    fun uiState_loggedIn_pinsEveryPostUnderAll() = runTest {
        val viewModel = mapViewModelOfPostsByEach()

        val state = viewModel.uiState.value

        assertEquals(PinFilter.ALL to postsByEach.map { it.id }, state.filterAndPinnedPostIds())
    }

    @Test
    fun uiState_loggedOut_offersNoFilter() = runTest {
        accountRepository.logOut()

        assertNull(mapViewModel().uiState.value.pinFilter)
    }

    @Test
    fun uiState_guestLogsIn_keepsThePinsItWrote() = runTest {
        accountRepository.logOut()
        val viewModel = mapViewModelOfPostsByEach()
        val pinnedPosts = viewModel.uiState.value.pinnedPosts

        accountRepository.logInAs(USER)

        assertSame(pinnedPosts, viewModel.uiState.value.pinnedPosts)
    }

    @Test
    fun uiState_guestLogsIn_offersFilterOnAll() = runTest {
        accountRepository.logOut()
        val viewModel = mapViewModelOfPostsByEach()

        accountRepository.logInAs(USER)

        assertEquals(PinFilter.ALL, viewModel.uiState.value.pinFilter)
    }

    @Test
    fun choosePinFilter_mine_pinsUsersOwnPostsOnly() = runTest {
        val viewModel = mapViewModelOfPostsByEach()

        viewModel.choosePinFilter(PinFilter.MINE)

        assertEquals(
            PinFilter.MINE to listOf(usersPost.id),
            viewModel.uiState.value.filterAndPinnedPostIds()
        )
    }

    @Test
    fun choosePinFilter_following_pinsPostsOfFollowedUsersOnly() = runTest {
        followRepository.addFollow(followerUid = USER, followedUid = FOLLOWED)
        followRepository.addFollow(followerUid = FOLLOWER, followedUid = USER)
        val viewModel = mapViewModelOfPostsByEach()

        viewModel.choosePinFilter(PinFilter.FOLLOWING)

        assertEquals(
            PinFilter.FOLLOWING to listOf(followedUsersPost.id),
            viewModel.uiState.value.filterAndPinnedPostIds()
        )
    }

    @Test
    fun choosePinFilter_userUnfollows_unpinsTheirPosts() = runTest {
        followRepository.addFollow(followerUid = USER, followedUid = FOLLOWED)
        val viewModel = mapViewModelOfPostsByEach()
        viewModel.choosePinFilter(PinFilter.FOLLOWING)

        followRepository.removeFollow(followerUid = USER, followedUid = FOLLOWED)

        assertEquals(emptyList<String>(), viewModel.uiState.value.pinnedPostIds())
    }

    @Test
    fun choosePinFilter_followedCannotBeRead_pinsNoPostsAndSaysSo() = runTest {
        followRepository.failsReads = true
        val viewModel = mapViewModelOfPostsByEach()

        viewModel.choosePinFilter(PinFilter.FOLLOWING)

        val state = viewModel.uiState.value
        assertTrue(state.followedUnknown)
        assertEquals(emptyList<String>(), state.pinnedPostIds())
    }

    @Test
    fun choosePinFilter_allAfterFollowedCannotBeRead_pinsEveryPost() = runTest {
        followRepository.failsReads = true
        val viewModel = mapViewModelOfPostsByEach()
        viewModel.choosePinFilter(PinFilter.FOLLOWING)

        viewModel.choosePinFilter(PinFilter.ALL)

        val state = viewModel.uiState.value
        assertEquals(
            false to postsByEach.map { it.id },
            state.followedUnknown to state.pinnedPostIds()
        )
    }

    @Test
    fun retryFollowed_afterReadFailed_pinsFollowedUsersPosts() = runTest {
        followRepository.failsReads = true
        val viewModel = mapViewModelOfPostsByEach()
        viewModel.choosePinFilter(PinFilter.FOLLOWING)
        followRepository.failsReads = false
        followRepository.addFollow(followerUid = USER, followedUid = FOLLOWED)

        viewModel.retryFollowed()

        val state = viewModel.uiState.value
        assertEquals(
            false to listOf(followedUsersPost.id),
            state.followedUnknown to state.pinnedPostIds()
        )
    }

    @Test
    fun retryFollowed_untilFollowedAreRead_saysNothingFailed() = runTest {
        followRepository.failsReads = true
        val viewModel = mapViewModelOfPostsByEach()
        viewModel.choosePinFilter(PinFilter.FOLLOWING)
        followRepository.readsAnswered = CompletableDeferred()

        viewModel.retryFollowed()

        assertFalse(viewModel.uiState.value.followedUnknown)
    }

    @Test
    fun choosePinFilter_followedNotReadYet_pinsNoPostsUnderFollowing() = runTest {
        followRepository.addFollow(followerUid = USER, followedUid = FOLLOWED)
        followRepository.readsAnswered = CompletableDeferred()
        val viewModel = mapViewModelOfPostsByEach()

        viewModel.choosePinFilter(PinFilter.FOLLOWING)

        assertEquals(
            PinFilter.FOLLOWING to emptyList<String>(),
            viewModel.uiState.value.filterAndPinnedPostIds()
        )
    }

    @Test
    fun uiState_mapShownAgainUnderFollowing_keepsTheirPinsWhileReadingThem() = runTest {
        followRepository.addFollow(followerUid = USER, followedUid = FOLLOWED)
        val viewModel = mapViewModelOfPostsByEach()
        viewModel.choosePinFilter(PinFilter.FOLLOWING)
        val pinnedPosts = viewModel.uiState.value.pinnedPosts
        viewModel.setMapShown(false)
        followRepository.readsAnswered = CompletableDeferred()

        viewModel.setMapShown(true)

        assertSame(pinnedPosts, viewModel.uiState.value.pinnedPosts)
    }

    @Test
    fun uiState_otherUserLogsInWhileMapIsHidden_startsOnAllWithEveryPost() = runTest {
        followRepository.addFollow(followerUid = USER, followedUid = FOLLOWED)
        val viewModel = mapViewModelOfPostsByEach()
        viewModel.choosePinFilter(PinFilter.FOLLOWING)
        viewModel.setMapShown(false)
        accountRepository.logOut()
        accountRepository.logInAs(OTHER_USER)

        viewModel.setMapShown(true)

        assertEquals(
            PinFilter.ALL to postsByEach.map { it.id },
            viewModel.uiState.value.filterAndPinnedPostIds()
        )
    }

    @Test
    fun uiState_sameUserLogsBackIn_keepsTheirFilter() = runTest {
        val viewModel = mapViewModelOfPostsByEach()
        viewModel.choosePinFilter(PinFilter.MINE)
        accountRepository.logOut()

        accountRepository.logInAs(USER)

        assertEquals(
            PinFilter.MINE to listOf(usersPost.id),
            viewModel.uiState.value.filterAndPinnedPostIds()
        )
    }

    @Test
    fun uiState_mapHidden_stopsReadingFollowed() = runTest {
        val viewModel = mapViewModelOfPostsByEach()
        viewModel.choosePinFilter(PinFilter.FOLLOWING)

        viewModel.setMapShown(false)

        assertEquals(0, followRepository.openReadCount)
    }

    @Test
    fun uiState_loggedOutWhileMineIsChosen_offersNoFilterAndPinsEveryPost() = runTest {
        val viewModel = mapViewModelOfPostsByEach()
        viewModel.choosePinFilter(PinFilter.MINE)

        accountRepository.logOut()

        assertEquals(
            null to postsByEach.map { it.id },
            viewModel.uiState.value.filterAndPinnedPostIds()
        )
    }

    @Test
    fun selectPost_postFilteredOut_opensItsPopupAnyway() = runTest {
        val viewModel = mapViewModelOfPostsByEach()
        viewModel.choosePinFilter(PinFilter.MINE)

        viewModel.selectPost(followersPost.id)

        assertEquals(postsByEach.indexOf(followersPost), viewModel.uiState.value.selectedRow)
    }

    // The map is shown and follows its posts while the test collects its state, as it does in the
    // app while Map shows it. No test draws the map, which needs MapLibre's native engine.
    private fun TestScope.mapViewModel(
        repository: PostRepository = FakePostRepository()
    ): MapViewModel {
        val viewModel = MapViewModel(
            postRepository = repository,
            pinFilterChoice = PinFilterChoice(accountRepository, followRepository),
            showOnMapRequest = showOnMapRequest,
            showInFeedRequest = showInFeedRequest,
            savedStateHandle = SavedStateHandle(),
            createMap = { _, _ -> error("Unit tests don't show the map") },
            postsDispatcher = UnconfinedTestDispatcher(testScheduler),
            sharing = SharingStarted.WhileSubscribed()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.setMapShown(true)
        return viewModel
    }

    // A map of posts by the user, by their follower, and by a user they follow.
    private fun TestScope.mapViewModelOfPostsByEach(): MapViewModel =
        mapViewModel(FakePostRepository(initialPosts = postsByEach))

    // The selected post's row and where the map moves next.
    private fun MapUiState.selection(): Pair<Int?, MapMove?> = selectedRow to pendingMove

    // The IDs of the posts the map pins, read from the GeoJSON it draws, in the pins' order.
    private fun MapUiState.pinnedPostIds(): List<String> {
        val json = (pinnedPosts.pins as GeoJsonData.JsonString).json
        return FeatureCollection.fromJson<Geometry, JsonObject?>(json).features
            .mapNotNull { it.id?.contentOrNull }
    }

    private fun MapUiState.filterAndPinnedPostIds(): Pair<PinFilter?, List<String>> =
        pinFilter to pinnedPostIds()
}
