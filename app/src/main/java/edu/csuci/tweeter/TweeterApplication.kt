package edu.csuci.tweeter

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras

/** The app process. It holds the one [AppContainer] that lives as long as the process does. */
class TweeterApplication : Application() {
    /** The shared objects, for ViewModel factories to read. */
    val container: AppContainer by lazy { AppContainer() }
}

/**
 * The app's [AppContainer], for use inside a ViewModel factory's `initializer { }`:
 * `FeedViewModel(appContainer.postRepository)`.
 */
val CreationExtras.appContainer: AppContainer
    get() = (checkNotNull(this[APPLICATION_KEY]) as TweeterApplication).container
