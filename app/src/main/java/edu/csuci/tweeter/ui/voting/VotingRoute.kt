package edu.csuci.tweeter.ui.voting

import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import edu.csuci.tweeter.ui.navigation.opaqueComposable
import kotlinx.serialization.Serializable

/**
 * The voting page of one post, opened by the post's animal name while votes disagree.
 *
 * @property postId The post's ID.
 */
@Serializable
data class VotingRoute(val postId: String)

/**
 * Registers [VotingScreen] under [VotingRoute] in the app's NavHost.
 *
 * @param onBack Returns to the screen it was opened from.
 * @param onOpenAnimal Opens the page of the animal with the given name.
 * @param onLogIn Opens Log in, for a guest who tries to vote.
 */
fun NavGraphBuilder.addVotingScreen(
    onBack: () -> Unit,
    onOpenAnimal: (String) -> Unit,
    onLogIn: () -> Unit
) {
    opaqueComposable<VotingRoute> { backStackEntry ->
        VotingScreen(
            postId = backStackEntry.toRoute<VotingRoute>().postId,
            onBack = onBack,
            onOpenAnimal = onOpenAnimal,
            onLogIn = onLogIn
        )
    }
}
