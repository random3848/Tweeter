package edu.csuci.tweeter.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.notification.NotificationPermissionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Gets the app ready to notify the logged-in user of posts near them, once a user is logged in:
 * asks [NotificationSetup] to request permission to show notifications if the app may not yet and
 * never asked, and updates where the device last was.
 *
 * @param accountRepository Who is logged in; guests get no notifications.
 * @param permissionRecord Whether the app already asked for the permission, which it asks once.
 * @param canNotify Whether the app may show notifications.
 * @param updateLastLocation Gets the device's location, if the user lets the app, and saves it as
 *     where the device last was, which notifications of posts near the user measure from.
 */
class NotificationSetupViewModel(
    accountRepository: AccountRepository,
    private val permissionRecord: NotificationPermissionRecord,
    private val canNotify: () -> Boolean,
    private val updateLastLocation: suspend () -> Unit
) : ViewModel() {
    private val _shouldRequestPermission = MutableStateFlow(false)

    /**
     * Whether [NotificationSetup] should request permission to show notifications now, then call
     * [onPermissionRequested].
     */
    val shouldRequestPermission: StateFlow<Boolean> = _shouldRequestPermission.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.loggedInUid.first { uid -> uid != null }
            _shouldRequestPermission.value = needsPermissionRequest()
            updateLastLocation()
        }
    }

    /** Records that the permission was requested, so that it never is again. */
    fun onPermissionRequested() {
        _shouldRequestPermission.value = false
        viewModelScope.launch { permissionRecord.recordRequest() }
    }

    private suspend fun needsPermissionRequest(): Boolean =
        !canNotify() && !permissionRecord.wasRequested()

    companion object {
        /** Creates a [NotificationSetupViewModel] that reads the app's shared objects. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                val container = application.container
                NotificationSetupViewModel(
                    accountRepository = container.accountRepository,
                    permissionRecord = container.notificationPermissionRecord,
                    canNotify = container.postNotifier::canNotify,
                    updateLastLocation = { container.deviceLocationProvider.currentFix() }
                )
            }
        }
    }
}
