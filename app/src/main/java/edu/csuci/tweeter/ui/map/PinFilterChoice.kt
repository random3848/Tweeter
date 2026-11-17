package edu.csuci.tweeter.ui.map

import android.util.Log
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.FollowException
import edu.csuci.tweeter.data.FollowRepository
import edu.csuci.tweeter.data.observeFriendUids
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update

/**
 * The filter a logged-in user chooses for Map's pins, and the authors it picks: everyone, the
 * user's friends, or the user alone. While nobody is logged in, the map pins every post and offers
 * no filter.
 *
 * @param accountRepository Who is logged in.
 * @param followRepository Who the logged-in user's friends are.
 */
class PinFilterChoice(
    private val accountRepository: AccountRepository,
    private val followRepository: FollowRepository
) {
    // The filter the user chose last, kept while nobody is logged in for when someone logs in.
    private val chosenFilter = MutableStateFlow(PinFilter.ALL)

    // How many times the user asked to read their friends again after reading them failed.
    private val friendsRetryCount = MutableStateFlow(0)

    /**
     * Whose posts the map pins, updated as the user chooses a filter, logs in or out, and as their
     * friends change. Their friends are read only while this is collected under
     * [PinFilter.FRIENDS].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun pinnedAuthors(): Flow<PinnedAuthors> =
        combine(accountRepository.loggedInUid, chosenFilter, ::Pair)
            .flatMapLatest { (uid, filter) ->
                if (uid == null) {
                    flowOf(PinnedAuthors.EveryoneForGuests)
                } else {
                    authorsPicked(filter, uid)
                }
            }

    /** Pins the posts of the authors [filter] picks from now on. */
    fun choose(filter: PinFilter) {
        chosenFilter.value = filter
    }

    /** Reads the user's friends again, if reading them failed. */
    fun retryFriends() {
        friendsRetryCount.update { it + 1 }
    }

    private fun authorsPicked(filter: PinFilter, uid: String): Flow<PinnedAuthors> = when (filter) {
        PinFilter.ALL -> flowOf(PinnedAuthors.Everyone)
        PinFilter.FRIENDS -> friendsOf(uid)
        PinFilter.MINE -> flowOf(PinnedAuthors.User(uid))
    }

    // Loading until they are read. Once they can't be, unknown until the user asks to retry.
    private fun friendsOf(uid: String): Flow<PinnedAuthors> =
        followRepository.observeFriendUids(uid)
            .map<Set<String>, PinnedAuthors> { friendUids ->
                PinnedAuthors.Friends(userUid = uid, uids = friendUids)
            }
            .onStart { emit(PinnedAuthors.LoadingFriends(userUid = uid)) }
            .retryWhen { error, _ ->
                if (error !is FollowException) {
                    return@retryWhen false
                }
                Log.e("PinFilterChoice", "Could not read the user's friends", error)
                // Read before saying so, so a retry asked for once the user sees it isn't missed.
                val retriesSoFar = friendsRetryCount.value
                emit(PinnedAuthors.UnknownFriends)
                friendsRetryCount.first { it > retriesSoFar }
                true
            }
}
