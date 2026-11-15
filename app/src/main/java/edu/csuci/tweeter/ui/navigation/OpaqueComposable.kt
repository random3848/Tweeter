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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
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
 *
 * Screens count themselves once composed, after the frame that composed them is laid out. Until
 * the app calls [finishFirstCount], after the first composition, the cover goes by
 * [hidesMapUntilCounted] instead: what it was when the activity was last saved, or true for a
 * new one. So the map under a screen doesn't count as shown in the first frame after the activity
 * is recreated, and the map shown on Map doesn't count as hidden.
 *
 * @param hidesMapUntilCounted Whether the cover hides the map before its first count.
 */
@Stable
class MapCover(private val hidesMapUntilCounted: Boolean) {
    private var screenCount by mutableIntStateOf(0)

    private var isCounted by mutableStateOf(false)

    /** Whether a screen is drawn over the map, so the user can't see or reach it. */
    val hidesMap: Boolean
        get() = if (isCounted) screenCount > 0 else hidesMapUntilCounted

    internal fun addScreen() {
        screenCount++
    }

    internal fun removeScreen() {
        screenCount--
    }

    /**
     * Goes by the screens counted from now on. Called after the first composition of the NavHost,
     * once the screens it composed have counted themselves.
     */
    fun finishFirstCount() {
        isCounted = true
    }

    /** Saves [MapCover]s across the activity being recreated. */
    companion object {
        /** Keeps whether the cover hides the map, which the restored cover goes by at first. */
        val Saver: Saver<MapCover, Boolean> = Saver(
            save = { cover -> cover.hidesMap },
            restore = { hidesMap -> MapCover(hidesMapUntilCounted = hidesMap) }
        )
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
 * Once Map has been opened, the map stays composed under the NavHost, so every screen but Map
 * hides it, even where the screen draws nothing. While the NavHost switches screens, the screen
 * being left keeps the map hidden until it is gone.
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
