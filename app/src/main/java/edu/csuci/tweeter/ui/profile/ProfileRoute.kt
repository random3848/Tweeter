package edu.csuci.tweeter.ui.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The route of [ProfileScreen], which the profile button on Feed's top bar opens. */
@Serializable
object ProfileRoute

/**
 * Registers [ProfileScreen] under [ProfileRoute] in the app's NavHost.
 *
 * @param onBack Leaves Profile for the screen it was opened from.
 */
fun NavGraphBuilder.addProfileScreen(onBack: () -> Unit) {
    composable<ProfileRoute> {
        ProfileScreen(onBack = onBack)
    }
}
