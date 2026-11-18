package edu.csuci.tweeter.ui.post

import androidx.navigation.NavGraphBuilder
import edu.csuci.tweeter.ui.navigation.opaqueComposable
import kotlinx.serialization.Serializable

/** The route of the camera screen for creating a photo post. */
@Serializable
object PostRoute

/**
 * Registers [PostScreen] under [PostRoute] in the app's NavHost.
 *
 * @param onLogIn Opens the login form.
 * @param onCreateAccount Opens the registration form.
 * @param onClose Leaves the camera screen.
 */
fun NavGraphBuilder.addPostScreen(
    onLogIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onClose: () -> Unit
) {
    opaqueComposable<PostRoute> {
        PostScreen(
            onLogIn = onLogIn,
            onCreateAccount = onCreateAccount,
            onClose = onClose
        )
    }
}
