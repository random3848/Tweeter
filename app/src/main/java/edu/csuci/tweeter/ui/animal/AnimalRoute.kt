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
data class AnimalRoute(val animal: String)

/**
 * Registers [AnimalScreen] under [AnimalRoute] in the app's NavHost.
 *
 * @param onBack Returns to the screen it was opened from.
 * @param onShowOnMap Opens Map, once the post the user chose is requested there.
 * @param onOpenProfile Opens the profile of the user with the given ID.
 * @param onLogIn Opens Log in, for a guest who tries to like or save a post.
 */
fun NavGraphBuilder.addAnimalScreen(
    onBack: () -> Unit,
    onShowOnMap: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLogIn: () -> Unit
) {
    opaqueComposable<AnimalRoute> { backStackEntry ->
        AnimalScreen(
            animal = backStackEntry.toRoute<AnimalRoute>().animal,
            onBack = onBack,
            onShowOnMap = onShowOnMap,
            onOpenProfile = onOpenProfile,
            onLogIn = onLogIn
        )
    }
}
