package edu.csuci.tweeter.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps the dark mode choice in [SharedPreferences].
 *
 * The file is read the first time [darkMode] is used, not when this is constructed, so
 * AppContainer can start that read on a background thread.
 */
class SharedPreferencesThemeRepository(private val preferences: SharedPreferences) :
    ThemeRepository {

    private val state by lazy {
        MutableStateFlow(
            if (preferences.contains(KEY_DARK_MODE)) {
                preferences.getBoolean(KEY_DARK_MODE, false)
            } else {
                null
            }
        )
    }

    override val darkMode: StateFlow<Boolean?> get() = state

    override fun setDarkMode(darkMode: Boolean?) {
        state.value = darkMode
        // apply() writes to disk in the background.
        val editor = preferences.edit()
        if (darkMode == null) {
            editor.remove(KEY_DARK_MODE)
        } else {
            editor.putBoolean(KEY_DARK_MODE, darkMode)
        }
        editor.apply()
    }

    companion object {
        /** The SharedPreferences file name where the dark mode choice is saved. */
        const val PREFS_NAME = "theme_settings"
        private const val KEY_DARK_MODE = "dark_mode"
    }
}
