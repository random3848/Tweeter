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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update

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
    private val followRepository: FollowRepository
) {
    // The filter chosen last, and who chose it.
    private val lastChoice = MutableStateFlow<UserChoice?>(null)

    // How many times the user asked to read their friends again after reading them failed.
    private val friendsRetryCount = MutableStateFlow(0)

    /** Whether the map offers the filter: while someone is logged in, as guests get none. */
    fun isOffered(): Flow<Boolean> =
        accountRepository.loggedInUid.map { uid -> uid != null }.distinctUntilChanged()

    /**
     * Whose posts the map pins, updated as the user chooses a filter, logs in or out, and as their
     * friends change. Their friends are read only while this is collected under
     * [PinFilter.FRIENDS].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun pinnedAuthors(): Flow<PinnedAuthors> = loggedInUsersChoice().flatMapLatest { choice ->
        if (choice == null) flowOf(PinnedAuthors.Everyone) else authorsPicked(choice)
    }

    /** Pins the posts of the authors [filter] picks, for the user logged in, from now on. */
    fun choose(filter: PinFilter) {
        // Guests get no filter to choose from.
        val uid = accountRepository.loggedInUid.value ?: return
        lastChoice.value = UserChoice(uid, filter)
    }

    /** Reads the user's friends again, if reading them failed. */
    fun retryFriends() {
        friendsRetryCount.update { it + 1 }
    }

    // The logged-in user and the filter they chose, or null while nobody is logged in.
    private fun loggedInUsersChoice(): Flow<UserChoice?> =
        combine(accountRepository.loggedInUid, lastChoice) { uid, choice ->
            uid?.let { UserChoice(it, filterChosenBy(it, choice)) }
        }.distinctUntilChanged()

    private fun filterChosenBy(uid: String, lastChoice: UserChoice?): PinFilter =
        if (lastChoice != null && lastChoice.uid == uid) lastChoice.filter else PinFilter.ALL

    private fun authorsPicked(choice: UserChoice): Flow<PinnedAuthors> = when (choice.filter) {
        PinFilter.ALL -> flowOf(PinnedAuthors.Everyone)
        PinFilter.FRIENDS -> friendsOf(choice.uid)
        PinFilter.MINE -> flowOf(PinnedAuthors.User(choice.uid))
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

    // A filter, and the user who chose it.
    private data class UserChoice(val uid: String, val filter: PinFilter)
}
