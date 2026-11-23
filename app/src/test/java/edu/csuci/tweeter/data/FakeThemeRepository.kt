package edu.csuci.tweeter.data

import edu.csuci.tweeter.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A [ThemeRepository] for tests: holds theme choice in memory. */
class FakeThemeRepository(initialThemeChoice: ThemeChoice = ThemeChoice.SYSTEM) : ThemeRepository {
    private val _themeChoice = MutableStateFlow(initialThemeChoice)
    override val themeChoice: StateFlow<ThemeChoice> = _themeChoice.asStateFlow()

    override fun setThemeChoice(themeChoice: ThemeChoice) {
        _themeChoice.value = themeChoice
    }
}
