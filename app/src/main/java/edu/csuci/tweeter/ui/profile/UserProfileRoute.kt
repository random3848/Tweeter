package edu.csuci.tweeter.ui.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/**
 * A profile opened by its user's ID.
 *
 * @property userUid The user whose profile should open.
 */
@Serializable
data class UserProfileRoute(val userUid: String)

/**
 * Registers the profile screen opened from a post's author.
 *
 * @param onBack Returns to the previous screen.
 * @param onLogIn Opens login for guests.
 * @param onCreateAccount Opens registration.
 * @param onEditProfile Opens the editor when viewing the owner's profile.
 */
fun NavGraphBuilder.addUserProfileScreen(
    onBack: () -> Unit,
    onLogIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onEditProfile: () -> Unit
) {
    composable<UserProfileRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<UserProfileRoute>()
        UserProfileScreen(
            userUid = route.userUid,
            onBack = onBack,
            onLogIn = onLogIn,
            onCreateAccount = onCreateAccount,
            onEditProfile = onEditProfile
        )
    }
}
