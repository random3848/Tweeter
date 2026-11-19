package edu.csuci.tweeter.ui.saved

import androidx.navigation.NavGraphBuilder
import edu.csuci.tweeter.ui.navigation.opaqueComposable
import kotlinx.serialization.Serializable

/** The route of [SavedPostsScreen], which Profile's "Saved posts" button opens. */
@Serializable
object SavedPostsRoute

/**
 * Registers [SavedPostsScreen] under [SavedPostsRoute] in the app's NavHost.
 *
 * @param onBack Returns to the screen it was opened from.
 * @param onShowOnMap Opens Map, once the post the user chose is requested there.
 * @param onOpenProfile Opens the profile of the user with the given ID.
 * @param onLogIn Opens Log in, for a guest who tries to like or save a post or vote.
 * @param onOpenAnimal Opens the page of the animal with the given name.
 * @param onOpenVoting Opens the voting page of the post with the given ID.
 */
fun NavGraphBuilder.addSavedPostsScreen(
    onBack: () -> Unit,
    onShowOnMap: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLogIn: () -> Unit,
    onOpenAnimal: (String) -> Unit,
    onOpenVoting: (String) -> Unit
) {
    opaqueComposable<SavedPostsRoute> {
        SavedPostsScreen(
            onBack = onBack,
            onShowOnMap = onShowOnMap,
            onOpenProfile = onOpenProfile,
            onLogIn = onLogIn,
            onOpenAnimal = onOpenAnimal,
            onOpenVoting = onOpenVoting
        )
    }
}
