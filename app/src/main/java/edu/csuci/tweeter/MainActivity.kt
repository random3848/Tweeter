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
import edu.csuci.tweeter.ui.theme.isDarkTheme

/** The app's only activity, which shows [TweeterApp]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as TweeterApplication).container.prepareFirstScreens()
        if (savedInstanceState == null) {
            (application as TweeterApplication).container.cameraFileStore.discardAll()
        }
        val themeRepository = (application as TweeterApplication).container.themeRepository
        setContent {
            val darkTheme = isDarkTheme(themeRepository)
            SideEffect {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            TweeterTheme(darkTheme) {
                TweeterApp()
            }
        }
    }
}
