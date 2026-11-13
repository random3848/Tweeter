package edu.csuci.tweeter.ui.auth

import androidx.navigation.NavGraphBuilder
import edu.csuci.tweeter.ui.navigation.opaqueComposable
import kotlinx.serialization.Serializable

/** The route to the email/password login form. */
@Serializable
object LogInRoute

/** The route to the email/password registration form. */
@Serializable
object CreateAccountRoute

/**
 * Registers the login form in the app's NavHost.
 *
 * @param onBack Returns to the previous screen.
 * @param onAuthenticated Returns to Profile after successful login.
 */
fun NavGraphBuilder.addLogInScreen(onBack: () -> Unit, onAuthenticated: () -> Unit) {
    opaqueComposable<LogInRoute> {
        AuthScreen(
            creatingAccount = false,
            onBack = onBack,
            onAuthenticated = onAuthenticated
        )
    }
}

/**
 * Registers the registration form in the app's NavHost.
 *
 * @param onBack Returns to the previous screen.
 * @param onAuthenticated Returns to Profile after successful registration.
 */
fun NavGraphBuilder.addCreateAccountScreen(onBack: () -> Unit, onAuthenticated: () -> Unit) {
    opaqueComposable<CreateAccountRoute> {
        AuthScreen(
            creatingAccount = true,
            onBack = onBack,
            onAuthenticated = onAuthenticated
        )
    }
}
