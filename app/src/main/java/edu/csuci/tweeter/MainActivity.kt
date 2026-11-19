package edu.csuci.tweeter

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import edu.csuci.tweeter.ui.TweeterApp
import edu.csuci.tweeter.ui.theme.ThemeSettings
import edu.csuci.tweeter.ui.theme.TweeterTheme

/** The app's only activity. It opens to an empty screen; the app has no screens yet. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            // The app starts afresh, so no screen it restores can still use a camera photo.
            (application as TweeterApplication).container.cameraFileStore.discardAll()
        }
        // The app is always light, so the system bars always use dark icons, even when the
        // device is in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        ThemeSettings.load(this)
        setContent {
            TweeterTheme {
                val darkTheme = ThemeSettings.darkMode ?: isSystemInDarkTheme()
                // The system bars' icons follow the app's theme: light icons in dark mode, dark
                // icons in light mode.
                DisposableEffect(darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = if (darkTheme) {
                            SystemBarStyle.dark(Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                        },
                        navigationBarStyle = if (darkTheme) {
                            SystemBarStyle.dark(Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                        }
                    )
                    onDispose {}
                }
                TweeterTheme {
                    // Screens go here.
                    TweeterApp()
                }
            }
        }
    }
}
