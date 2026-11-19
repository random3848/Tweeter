package edu.csuci.tweeter

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import edu.csuci.tweeter.ui.map.configureMapTiles

/**
 * The app process. It holds the one [AppContainer] that lives as long as the process does.
 *
 * It is registered in AndroidManifest.xml (`android:name`), so Android creates it before any
 * screen. That makes [onCreate] the place for setup that must happen before the first screen,
 * such as how the map downloads its tiles. It also makes the image loader every screen's photos
 * load with ([newImageLoader]), and WorkManager's configuration ([workManagerConfiguration]).
 */
class TweeterApplication :
    Application(),
    ImageLoaderFactory,
    Configuration.Provider {
    /** The shared objects, for ViewModel factories to read. */
    val container: AppContainer by lazy { AppContainer(this) }

    // WorkManager is started on its first use, on a background thread (see
    // AppContainer.schedulePostChecks), rather than before onCreate on the main thread: the
    // manifest removes its startup initializer. It creates the app's workers with the container's
    // factory.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(container.workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        configureMapTiles()
        container.prepareFirstScreens()
        container.schedulePostChecks()
        container.createPostNotificationChannels()
    }

    // Firebase Storage sends photos with `Cache-Control: private, max-age=0`, so by default Coil
    // asks Storage again (a conditional request) each time it takes a photo from its disk cache,
    // and a photo scrolled back to waits for the network. The app's photos never change at their
    // URL, because each upload gets a new file name, so the stored copy is shown without asking.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).respectCacheHeaders(false).build()
}
