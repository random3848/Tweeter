package edu.csuci.tweeter.data

import android.content.SharedPreferences
import edu.csuci.tweeter.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps the theme choice in [SharedPreferences].
 *
 * The file is read the first time [themeChoice] is used, not when this is constructed, so
 * AppContainer can start that read on a background thread.
 */
class SharedPreferencesThemeRepository(private val preferences: SharedPreferences) :
    ThemeRepository {

    private val state by lazy {
        MutableStateFlow(
            try {
                val name = preferences.getString(KEY_THEME_CHOICE, null)
                if (name != null) ThemeChoice.valueOf(name) else ThemeChoice.LIGHT
            } catch (_: IllegalArgumentException) {
                ThemeChoice.LIGHT
            }
        )
    }

    override val themeChoice: StateFlow<ThemeChoice> get() = state

    override fun setThemeChoice(themeChoice: ThemeChoice) {
        state.value = themeChoice
        preferences.edit()
            .putString(KEY_THEME_CHOICE, themeChoice.name)
            .apply()
    }

    companion object {
        /** The SharedPreferences file name where theme settings are saved. */
        const val PREFS_NAME = "theme_settings"
        private const val KEY_THEME_CHOICE = "theme_choice"
    }
}
