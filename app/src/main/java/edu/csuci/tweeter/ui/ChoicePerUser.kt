package edu.csuci.tweeter.ui

import edu.csuci.tweeter.data.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A choice each user makes for themselves, such as which posts a screen shows. Each user starts
 * on [default], and gets back what they chose when they log in again, unless someone else chose
 * since. Guests make one too, which the screen may limit, as Feed does with Friends.
 *
 * @param accountRepository Who is logged in.
 * @param default What a user who hasn't chosen gets.
 */
class ChoicePerUser<T>(private val accountRepository: AccountRepository, private val default: T) {
    // The choice made last, and who made it.
    private val lastChoice = MutableStateFlow<UserChoice<T>?>(null)

    /** Who is using the app, a user or a guest, and what they chose. */
    fun currentChoice(): Flow<UserChoice<T>> =
        combine(accountRepository.loggedInUid, lastChoice) { uid, lastChoice ->
            UserChoice(uid, valueChosenBy(uid, lastChoice))
        }.distinctUntilChanged()

    /** Makes [value] the choice of whoever is using the app, a user or a guest, from now on. */
    fun choose(value: T) {
        lastChoice.value = UserChoice(accountRepository.loggedInUid.value, value)
    }

    private fun valueChosenBy(uid: String?, lastChoice: UserChoice<T>?): T =
        if (lastChoice != null && lastChoice.uid == uid) lastChoice.value else default
}

/**
 * What a user or a guest chose, with [ChoicePerUser].
 *
 * @property uid The user's ID, or null for a guest.
 * @property value What they chose.
 */
data class UserChoice<T>(val uid: String?, val value: T)
