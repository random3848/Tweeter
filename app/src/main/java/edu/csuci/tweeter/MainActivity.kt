package edu.csuci.tweeter

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import edu.csuci.tweeter.ui.TweeterApp
import edu.csuci.tweeter.ui.theme.TweeterTheme
import edu.csuci.tweeter.ui.theme.useDarkTheme

/** The app's only activity. It opens to an empty screen; the app has no screens yet. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            // The app starts afresh, so no screen it restores can still use a camera photo.
            (application as TweeterApplication).container.cameraFileStore.discardAll()
        }
        val themeRepository = (application as TweeterApplication).container.themeRepository
        setContent {
            val darkTheme = useDarkTheme(themeRepository)
            // The system bars' icons follow the app's theme: light icons in dark mode, dark
            // icons in light mode.
            SideEffect {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            TweeterTheme(darkTheme) {
                // Screens go here.
                TweeterApp()
            }
        }
    }
}
