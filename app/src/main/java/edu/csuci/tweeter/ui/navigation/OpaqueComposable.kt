package edu.csuci.tweeter.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * The screens drawn over the map under the NavHost, which hide it. Each screen that
 * [opaqueComposable] registers counts while the NavHost composes it, so [hidesMap] turns true in
 * the frame the NavHost first draws one over the map, and false once it has removed the last one.
 * The back stack changes a few frames before either.
 */
@Stable
class MapCover {
    private var screenCount by mutableIntStateOf(0)

    /** Whether a screen is drawn over the map, so the user can't see or reach it. */
    val hidesMap: Boolean
        get() = screenCount > 0

    internal fun addScreen() {
        screenCount++
    }

    internal fun removeScreen() {
        screenCount--
    }
}

/** The [MapCover] of the NavHost that [opaqueComposable] screens are drawn in. */
val LocalMapCover = staticCompositionLocalOf<MapCover> {
    error("Opaque screens are drawn only inside a NavHost that provides a MapCover")
}

/**
 * Registers [content] as the screen of route [T], as `composable` does, drawn on the app's
 * background, and counted in [LocalMapCover] while it's composed.
 *
 * The map stays composed under the NavHost, so every screen but Map hides it, even where the
 * screen draws nothing. While the NavHost switches screens, the screen being left keeps the map
 * hidden until it is gone.
 */
inline fun <reified T : Any> NavGraphBuilder.opaqueComposable(
    noinline content: @Composable () -> Unit
) {
    composable<T> { OpaqueScreen(content) }
}

/** [content] on the app's background, counted in [LocalMapCover] while it's composed. */
@PublishedApi
@Composable
internal fun OpaqueScreen(content: @Composable () -> Unit) {
    val mapCover = LocalMapCover.current
    DisposableEffect(mapCover) {
        mapCover.addScreen()
        onDispose { mapCover.removeScreen() }
    }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        content()
    }
}
