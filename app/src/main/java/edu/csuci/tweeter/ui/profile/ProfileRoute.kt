package edu.csuci.tweeter.ui.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The route of [ProfileScreen], which the profile button on Feed's top bar opens. */
@Serializable
object ProfileRoute

/**
 * Registers Profile and its authentication navigation callbacks.
 *
 * @param onBack Returns to the previous screen.
 * @param onLogIn Opens the login form.
 * @param onCreateAccount Opens the registration form.
 * @param onEditProfile Opens Edit profile.
 */
fun NavGraphBuilder.addProfileScreen(
    onBack: () -> Unit,
    onLogIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onEditProfile: () -> Unit
) {
    composable<ProfileRoute> {
        ProfileScreen(
            onBack = onBack,
            onLogIn = onLogIn,
            onCreateAccount = onCreateAccount,
            onEditProfile = onEditProfile
        )
    }
}
