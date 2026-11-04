package edu.csuci.tweeter.ui.profile

import androidx.navigation.NavGraphBuilder
import edu.csuci.tweeter.ui.navigation.opaqueComposable
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
    opaqueComposable<ProfileRoute> {
        ProfileScreen(
            onBack = onBack,
            onLogIn = onLogIn,
            onCreateAccount = onCreateAccount,
            onEditProfile = onEditProfile
        )
    }
}
