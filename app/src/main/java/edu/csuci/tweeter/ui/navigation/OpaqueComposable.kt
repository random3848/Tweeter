package edu.csuci.tweeter.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Registers [content] as the screen of route [T], as `composable` does, drawn on the app's
 * background.
 *
 * The map stays composed under the NavHost, so every screen but Map hides it, even where the
 * screen draws nothing. While the NavHost switches screens, the screen being left keeps the map
 * hidden until it is gone.
 */
inline fun <reified T : Any> NavGraphBuilder.opaqueComposable(
    noinline content: @Composable () -> Unit
) {
    composable<T> {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            content()
        }
    }
}
