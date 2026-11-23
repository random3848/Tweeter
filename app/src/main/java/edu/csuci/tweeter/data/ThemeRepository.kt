package edu.csuci.tweeter.data

import edu.csuci.tweeter.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.StateFlow

/** The user's dark mode choice, kept across app restarts. */
interface ThemeRepository {
    /** The current theme choice (System, Light, or Dark). */
    val themeChoice: StateFlow<ThemeChoice>

    /** Saves the choice. */
    fun setThemeChoice(themeChoice: ThemeChoice)
}
