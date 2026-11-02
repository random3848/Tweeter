package edu.csuci.tweeter.ui.map

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The route of [MapScreen], which shows every post as a pin. */
@Serializable
object MapRoute

/**
 * Registers [MapScreen] under [MapRoute] in the app's NavHost.
 *
 * @param onShowInFeed Opens Home, where the post whose popup the user pressed is selected.
 */
fun NavGraphBuilder.addMapScreen(onShowInFeed: () -> Unit) {
    composable<MapRoute> {
        MapScreen(onShowInFeed = onShowInFeed)
    }
}
