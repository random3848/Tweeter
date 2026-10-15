package edu.csuci.tweeter

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import edu.csuci.tweeter.ui.TweeterApp
import edu.csuci.tweeter.ui.theme.TweeterTheme

/** The app's only activity. Every screen is a Compose destination inside [TweeterApp]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always light, so the system bars always use dark icons, even when the
        // device is in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            TweeterTheme {
                TweeterApp()
                // Screens go here
            }
        }
    }
}
