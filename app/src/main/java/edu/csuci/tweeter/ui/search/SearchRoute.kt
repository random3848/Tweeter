package edu.csuci.tweeter.ui.search

import androidx.navigation.NavGraphBuilder
import edu.csuci.tweeter.ui.navigation.opaqueComposable
import kotlinx.serialization.Serializable

/** The route of [SearchScreen], the Search tab. */
@Serializable
object SearchRoute

/**
 * Registers [SearchScreen] under [SearchRoute] in the app's NavHost.
 *
 * @param onOpenProfile Opens the profile of the user with the given ID.
 * @param onOpenAnimal Opens the posts of the animal with the given name.
 */
fun NavGraphBuilder.addSearchScreen(
    onOpenProfile: (String) -> Unit,
    onOpenAnimal: (String) -> Unit
) {
    opaqueComposable<SearchRoute> {
        SearchScreen(onOpenProfile = onOpenProfile, onOpenAnimal = onOpenAnimal)
    }
}
