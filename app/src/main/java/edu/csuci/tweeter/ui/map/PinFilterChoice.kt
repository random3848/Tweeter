package edu.csuci.tweeter.ui.map

import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.FollowRepository
import edu.csuci.tweeter.ui.ChoicePerUser
import edu.csuci.tweeter.ui.following.FollowedUsers
import edu.csuci.tweeter.ui.following.FollowedUsersReader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The filter a logged-in user chooses for Map's pins, and the authors it picks: everyone, the
 * users the user follows, or the user alone. Each user starts on [PinFilter.ALL], and gets back the
 * filter they chose when they log in again, unless someone else chose one since. While nobody is
 * logged in, the map pins every post and offers no filter.
 *
 * @param accountRepository Who is logged in.
 * @param followRepository Who the logged-in user follows.
 */
class PinFilterChoice(
    private val accountRepository: AccountRepository,
    followRepository: FollowRepository
) {
    private val choice = ChoicePerUser(accountRepository, default = PinFilter.ALL)

    private val followedUsersReader = FollowedUsersReader(followRepository)

    /** Whether the map offers the filter: while someone is logged in, as guests get none. */
    fun isOffered(): Flow<Boolean> =
        accountRepository.loggedInUid.map { uid -> uid != null }.distinctUntilChanged()

    /**
     * Whose posts the map pins, updated as the user chooses a filter, logs in or out, and as who
     * they follow changes. Who they follow is read only while this is collected under
     * [PinFilter.FOLLOWING].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun pinnedAuthors(): Flow<PinnedAuthors> = choice.currentChoice().flatMapLatest { chosen ->
        val uid = chosen.uid
        if (uid == null) flowOf(PinnedAuthors.Everyone) else authorsPicked(uid, chosen.value)
    }

    /**
     * Pins the posts of the authors [filter] picks, for the user logged in, from now on. Guests
     * aren't offered a filter.
     */
    fun choose(filter: PinFilter) {
        choice.choose(filter)
    }

    /** Reads who the user follows again, if reading it failed. */
    fun retryFollowed() {
        followedUsersReader.retry()
    }

    private fun authorsPicked(uid: String, filter: PinFilter): Flow<PinnedAuthors> = when (filter) {
        PinFilter.ALL -> flowOf(PinnedAuthors.Everyone)
        PinFilter.FOLLOWING -> followedUsersReader.followedBy(uid).map(::pinnedAuthorsOf)
        PinFilter.MINE -> flowOf(PinnedAuthors.User(uid))
    }

    private fun pinnedAuthorsOf(followed: FollowedUsers): PinnedAuthors = when (followed) {
        is FollowedUsers.Known -> PinnedAuthors.Followed(followed.uids)
        FollowedUsers.Loading -> PinnedAuthors.LoadingFollowed
        FollowedUsers.Unknown -> PinnedAuthors.UnknownFollowed
    }
}
