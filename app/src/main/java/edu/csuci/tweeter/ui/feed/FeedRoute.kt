package edu.csuci.tweeter.ui.feed

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The route of [FeedScreen], the screen the app opens to. */
@Serializable
object FeedRoute

/**
 * Registers [FeedScreen] under [FeedRoute] in the app's NavHost.
 *
 * @param onShowOnMap Opens Map, where the post the user chose to show is requested.
 * @param onOpenProfile Opens the selected author's profile.
 */
fun NavGraphBuilder.addFeedScreen(onShowOnMap: () -> Unit, onOpenProfile: (String) -> Unit) {
    composable<FeedRoute> {
        FeedScreen(
            onShowOnMap = onShowOnMap,
            onOpenProfile = onOpenProfile
        )
    }
}
