package edu.csuci.tweeter.ui

import edu.csuci.tweeter.data.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A choice each logged-in user makes for themselves, such as which posts a screen shows. Each
 * user starts on [default], and gets back what they chose when they log in again, unless someone
 * else chose since. Guests have no choice to make.
 *
 * @param accountRepository Who is logged in.
 * @param default What a user who hasn't chosen gets.
 */
class ChoicePerUser<T>(private val accountRepository: AccountRepository, private val default: T) {
    // The choice made last, and who made it.
    private val lastChoice = MutableStateFlow<UserChoice<T>?>(null)

    /** The logged-in user and what they chose, or null while nobody is logged in. */
    fun loggedInUsersChoice(): Flow<UserChoice<T>?> =
        combine(accountRepository.loggedInUid, lastChoice) { uid, lastChoice ->
            uid?.let { UserChoice(it, valueChosenBy(it, lastChoice)) }
        }.distinctUntilChanged()

    /** Makes [value] the logged-in user's choice from now on. Guests have none to make. */
    fun choose(value: T) {
        val uid = accountRepository.loggedInUid.value ?: return
        lastChoice.value = UserChoice(uid, value)
    }

    private fun valueChosenBy(uid: String, lastChoice: UserChoice<T>?): T =
        if (lastChoice != null && lastChoice.uid == uid) lastChoice.value else default
}

/**
 * What a user chose, with [ChoicePerUser].
 *
 * @property uid The user's ID.
 * @property value What they chose.
 */
data class UserChoice<T>(val uid: String, val value: T)
