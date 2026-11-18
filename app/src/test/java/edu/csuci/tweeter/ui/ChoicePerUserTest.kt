@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui

import edu.csuci.tweeter.data.FakeAccountRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val MAYA = "maya"
private const val JORDAN = "jordan"
private const val DEFAULT = "default"
private const val CHOSEN = "chosen"

class ChoicePerUserTest {
    @Test
    fun currentChoice_nothingChosen_isDefault() = runTest {
        val choice = ChoicePerUser(FakeAccountRepository(MAYA), DEFAULT)

        assertEquals(UserChoice(MAYA, DEFAULT), choice.currentChoice().first())
    }

    @Test
    fun choose_keepsItForTheUser() = runTest {
        val choice = ChoicePerUser(FakeAccountRepository(MAYA), DEFAULT)

        choice.choose(CHOSEN)

        assertEquals(UserChoice(MAYA, CHOSEN), choice.currentChoice().first())
    }

    @Test
    fun currentChoice_otherUserLogsIn_startsOnDefault() = runTest {
        val account = FakeAccountRepository(MAYA)
        val choice = ChoicePerUser(account, DEFAULT)
        choice.choose(CHOSEN)

        account.logInAs(JORDAN)

        assertEquals(UserChoice(JORDAN, DEFAULT), choice.currentChoice().first())
    }

    @Test
    fun currentChoice_userLogsInAgain_getsTheirChoiceBack() = runTest {
        val account = FakeAccountRepository(MAYA)
        val choice = ChoicePerUser(account, DEFAULT)
        choice.choose(CHOSEN)
        account.logOut()

        account.logInAs(MAYA)

        assertEquals(UserChoice(MAYA, CHOSEN), choice.currentChoice().first())
    }

    @Test
    fun currentChoice_guestHasNotChosen_isDefaultWithNoUser() = runTest {
        val choice = ChoicePerUser(FakeAccountRepository(initialUid = null), DEFAULT)

        assertEquals(UserChoice(uid = null, value = DEFAULT), choice.currentChoice().first())
    }

    @Test
    fun choose_guest_keepsItForGuests() = runTest {
        val choice = ChoicePerUser(FakeAccountRepository(initialUid = null), DEFAULT)

        choice.choose(CHOSEN)

        assertEquals(UserChoice(uid = null, value = CHOSEN), choice.currentChoice().first())
    }

    @Test
    fun choose_guest_userWhoLogsInStartsOnDefault() = runTest {
        val account = FakeAccountRepository(initialUid = null)
        val choice = ChoicePerUser(account, DEFAULT)
        choice.choose(CHOSEN)

        account.logInAs(MAYA)

        assertEquals(UserChoice(MAYA, DEFAULT), choice.currentChoice().first())
    }
}
