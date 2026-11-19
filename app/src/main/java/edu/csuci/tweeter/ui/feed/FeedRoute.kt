package edu.csuci.tweeter.ui.feed

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import edu.csuci.tweeter.ui.navigation.opaqueComposable
import kotlinx.serialization.Serializable

/** The route of [FeedScreen], the screen the app opens to. */
@Serializable
object FeedRoute

/**
 * Registers [FeedScreen] under [FeedRoute] in the app's NavHost.
 *
 * @param onShowOnMap Opens Map, where the post the user chose to show is requested.
 * @param onOpenProfile Opens the selected author's profile.
 * @param onLogIn Opens Log in, for a guest who tries to like a post or vote.
 * @param onOpenAnimal Opens the page of the animal with the given name.
 * @param onOpenVoting Opens the voting page of the post with the given ID.
 * @param topBarActions Buttons on the right of the feed's top bar, such as the profile button.
 */
fun NavGraphBuilder.addFeedScreen(
    onShowOnMap: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLogIn: () -> Unit,
    onOpenAnimal: (String) -> Unit,
    onOpenVoting: (String) -> Unit,
    topBarActions: @Composable RowScope.() -> Unit
) {
    opaqueComposable<FeedRoute> {
        FeedScreen(
            onShowOnMap = onShowOnMap,
            onOpenProfile = onOpenProfile,
            onLogIn = onLogIn,
            onOpenAnimal = onOpenAnimal,
            onOpenVoting = onOpenVoting,
            topBarActions = topBarActions
        )
    }
}
