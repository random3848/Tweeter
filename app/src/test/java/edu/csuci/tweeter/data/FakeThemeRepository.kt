package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A [ThemeRepository] for tests: holds theme choice in memory. */
class FakeThemeRepository(initialDarkMode: Boolean? = null) : ThemeRepository {
    private val _darkMode = MutableStateFlow(initialDarkMode)
    override val darkMode: StateFlow<Boolean?> = _darkMode.asStateFlow()

    override fun setDarkMode(darkMode: Boolean?) {
        _darkMode.value = darkMode
    }
}
