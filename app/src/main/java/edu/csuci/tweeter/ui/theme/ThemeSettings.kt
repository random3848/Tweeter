package edu.csuci.tweeter.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The user's dark mode choice, kept across app restarts. */
object ThemeSettings {
    private const val PREFS = "theme_settings"
    private const val KEY_DARK = "dark_mode"

    /** True or false once the user has picked; null means follow the phone's setting. */
    var darkMode: Boolean? by mutableStateOf(null)
        private set

    /** Reads the saved choice. Call once before the UI is shown. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        darkMode = if (prefs.contains(KEY_DARK)) prefs.getBoolean(KEY_DARK, false) else null
    }

    /** Switches the theme and saves the choice. */
    fun set(context: Context, dark: Boolean) {
        darkMode = dark
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, dark).apply()
    }
}
