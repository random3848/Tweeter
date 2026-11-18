package edu.csuci.tweeter.ui.map

import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.FollowRepository
import edu.csuci.tweeter.ui.ChoicePerUser
import edu.csuci.tweeter.ui.UserChoice
import edu.csuci.tweeter.ui.friends.FriendsReader
import edu.csuci.tweeter.ui.friends.UserFriends
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The filter a logged-in user chooses for Map's pins, and the authors it picks: everyone, the
 * user's friends, or the user alone. Each user starts on [PinFilter.ALL], and gets back the
 * filter they chose when they log in again, unless someone else chose one since. While nobody is
 * logged in, the map pins every post and offers no filter.
 *
 * @param accountRepository Who is logged in.
 * @param followRepository Who the logged-in user's friends are.
 */
class PinFilterChoice(
    private val accountRepository: AccountRepository,
    followRepository: FollowRepository
) {
    private val choice = ChoicePerUser(accountRepository, default = PinFilter.ALL)

    private val friendsReader = FriendsReader(followRepository)

    /** Whether the map offers the filter: while someone is logged in, as guests get none. */
    fun isOffered(): Flow<Boolean> =
        accountRepository.loggedInUid.map { uid -> uid != null }.distinctUntilChanged()

    /**
     * Whose posts the map pins, updated as the user chooses a filter, logs in or out, and as their
     * friends change. Their friends are read only while this is collected under
     * [PinFilter.FRIENDS].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun pinnedAuthors(): Flow<PinnedAuthors> =
        choice.loggedInUsersChoice().flatMapLatest { chosen ->
            if (chosen == null) flowOf(PinnedAuthors.Everyone) else authorsPicked(chosen)
        }

    /** Pins the posts of the authors [filter] picks, for the user logged in, from now on. */
    fun choose(filter: PinFilter) {
        choice.choose(filter)
    }

    /** Reads the user's friends again, if reading them failed. */
    fun retryFriends() {
        friendsReader.retry()
    }

    private fun authorsPicked(chosen: UserChoice<PinFilter>): Flow<PinnedAuthors> =
        when (chosen.value) {
            PinFilter.ALL -> flowOf(PinnedAuthors.Everyone)
            PinFilter.FRIENDS -> friendsReader.friendsOf(chosen.uid).map(::pinnedAuthorsOf)
            PinFilter.MINE -> flowOf(PinnedAuthors.User(chosen.uid))
        }

    private fun pinnedAuthorsOf(friends: UserFriends): PinnedAuthors = when (friends) {
        is UserFriends.Known -> PinnedAuthors.Friends(friends.uids)
        UserFriends.Loading -> PinnedAuthors.LoadingFriends
        UserFriends.Unknown -> PinnedAuthors.UnknownFriends
    }
}
