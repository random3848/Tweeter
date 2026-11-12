package edu.csuci.tweeter.ui.map

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The route of the Map tab, where [MapScreen] shows every post as a pin. */
@Serializable
object MapRoute

/**
 * Registers the Map tab under [MapRoute] in the app's NavHost. The tab draws nothing itself:
 * [MapScreen], which the app composes under the NavHost as Map first opens, shows through it.
 */
fun NavGraphBuilder.addMapScreen() {
    composable<MapRoute> {}
}
