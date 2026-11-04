package edu.csuci.tweeter.ui.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The route of [EditProfileScreen], which Profile's "Edit profile" button opens. */
@Serializable
object EditProfileRoute

/**
 * Registers [EditProfileScreen] under [EditProfileRoute] in the app's NavHost.
 *
 * @param onClose Returns to Profile, after saving or cancelling.
 */
fun NavGraphBuilder.addEditProfileScreen(onClose: () -> Unit) {
    composable<EditProfileRoute> {
        EditProfileScreen(onClose = onClose)
    }
}
