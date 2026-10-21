package edu.csuci.tweeter.ui.post

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The route of the screen for creating a photo post. */
@Serializable
object PostRoute

/** Registers [PostScreen] under [PostRoute] in the app's NavHost. */
fun NavGraphBuilder.addPostScreen() {
    composable<PostRoute> {
        PostScreen()
    }
}
