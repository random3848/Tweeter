package edu.csuci.tweeter.ui.animal

import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import edu.csuci.tweeter.ui.navigation.opaqueComposable
import kotlinx.serialization.Serializable

/**
 * The posts of one animal, opened by its name.
 *
 * @property animal The animal's name, as posts name it.
 */
@Serializable
data class AnimalPostsRoute(val animal: String)

/**
 * Registers [AnimalPostsScreen] under [AnimalPostsRoute] in the app's NavHost.
 *
 * @param onBack Returns to the screen it was opened from.
 * @param onShowOnMap Opens Map, once the post the user chose is requested there.
 * @param onOpenProfile Opens the profile of the user with the given ID.
 */
fun NavGraphBuilder.addAnimalPostsScreen(
    onBack: () -> Unit,
    onShowOnMap: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    opaqueComposable<AnimalPostsRoute> { backStackEntry ->
        AnimalPostsScreen(
            animal = backStackEntry.toRoute<AnimalPostsRoute>().animal,
            onBack = onBack,
            onShowOnMap = onShowOnMap,
            onOpenProfile = onOpenProfile
        )
    }
}
