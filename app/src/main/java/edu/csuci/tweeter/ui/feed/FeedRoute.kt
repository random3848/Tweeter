package edu.csuci.tweeter.ui.feed

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
 */
fun NavGraphBuilder.addFeedScreen(onShowOnMap: () -> Unit) {
    opaqueComposable<FeedRoute> {
        FeedScreen(onShowOnMap = onShowOnMap)
    }
}
