@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.settings

import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeThemeRepository
import edu.csuci.tweeter.ui.theme.ThemeChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {
    private val themeRepository = FakeThemeRepository(initialThemeChoice = ThemeChoice.LIGHT)
    private val accountRepository = FakeAccountRepository()

    @Before
    fun useTestMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun setThemeChoice_updatesThemeRepository() = runTest {
        val viewModel = SettingsViewModel(accountRepository, themeRepository)

        assertEquals(ThemeChoice.LIGHT, viewModel.themeChoice.first())

        viewModel.setThemeChoice(ThemeChoice.DARK)
        assertEquals(ThemeChoice.DARK, viewModel.themeChoice.first())
    }
}
