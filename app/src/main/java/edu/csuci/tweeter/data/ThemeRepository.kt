package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.StateFlow

/** The user's dark mode choice, kept across app restarts. */
interface ThemeRepository {
    /** True for dark, false for light, null to follow the phone's setting. */
    val darkMode: StateFlow<Boolean?>

    /** Saves the choice; null goes back to following the phone. */
    fun setDarkMode(darkMode: Boolean?)
}
