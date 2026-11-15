package edu.csuci.tweeter.ui.post

import androidx.navigation.NavGraphBuilder
import edu.csuci.tweeter.ui.navigation.opaqueComposable
import kotlinx.serialization.Serializable

/** The route of the screen for creating a photo post. */
@Serializable
object PostRoute

/**
 * Registers [PostScreen] under [PostRoute] in the app's NavHost.
 *
 * @param onLogIn Opens the login form.
 * @param onCreateAccount Opens the registration form.
 */
fun NavGraphBuilder.addPostScreen(onLogIn: () -> Unit, onCreateAccount: () -> Unit) {
    opaqueComposable<PostRoute> {
        PostScreen(onLogIn = onLogIn, onCreateAccount = onCreateAccount)
    }
}
