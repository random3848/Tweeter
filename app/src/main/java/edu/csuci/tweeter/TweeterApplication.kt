package edu.csuci.tweeter

import android.app.Application

/**
 * The app process. It holds the one [AppContainer] that lives as long as the process does.
 *
 * It is registered in AndroidManifest.xml (`android:name`), so Android creates it before any
 * screen.
 */
class TweeterApplication : Application() {
    /** The shared objects, for ViewModel factories to read. */
    val container: AppContainer by lazy { AppContainer() }
}
