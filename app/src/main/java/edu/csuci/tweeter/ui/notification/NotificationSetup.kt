package edu.csuci.tweeter.ui.notification

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Gets the app ready to notify a logged-in user about posts, such as rare animals posted near them
 * and popular posts; it draws nothing. The first time a user is logged in on the screen that shows
 * it (Feed), it asks them for permission to show notifications, once ever, for every kind: if they
 * say no, the app doesn't ask again, and they can still allow it in the system's settings. It also
 * updates where the device last was, which notifications of posts near the user measure from, if
 * the user lets the app get their location.
 *
 * @param viewModel Decides when to ask, and remembers that it did.
 */
@Composable
fun NotificationSetup(
    viewModel: NotificationSetupViewModel = viewModel(factory = NotificationSetupViewModel.factory)
) {
    // Whatever the user answers, there is nothing more to do: checks for posts to notify the user
    // of ask whether the app may show notifications each time they run.
    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) {}
    val shouldRequestPermission by viewModel.shouldRequestPermission.collectAsStateWithLifecycle()
    LaunchedEffect(shouldRequestPermission) {
        if (shouldRequestPermission) {
            viewModel.onPermissionRequested()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
