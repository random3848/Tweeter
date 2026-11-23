package edu.csuci.tweeter.ui.settings

import androidx.navigation.NavGraphBuilder
import edu.csuci.tweeter.ui.navigation.opaqueComposable
import kotlinx.serialization.Serializable

/** The Settings destination opened from the owner's profile. */
@Serializable
object SettingsRoute

/**
 * Registers account settings.
 *
 * @param onBack Returns to the previous screen.
 * @param onLoggedOut Opens Profile after logout.
 */
fun NavGraphBuilder.addSettingsScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    opaqueComposable<SettingsRoute> {
        SettingsScreen(
            onBack = onBack,
            onLoggedOut = onLoggedOut
        )
    }
}
