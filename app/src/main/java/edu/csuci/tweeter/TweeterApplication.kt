package edu.csuci.tweeter

import android.app.Application
import edu.csuci.tweeter.ui.map.configureMapTiles

/**
 * The app process. It holds the one [AppContainer] that lives as long as the process does.
 *
 * It is registered in AndroidManifest.xml (`android:name`), so Android creates it before any
 * screen. That makes [onCreate] the place for setup that must happen before the first screen,
 * such as how the map downloads its tiles.
 */
class TweeterApplication : Application() {
    /** The shared objects, for ViewModel factories to read. */
    val container: AppContainer by lazy { AppContainer() }

    override fun onCreate() {
        super.onCreate()
        configureMapTiles(this)
    }
}
