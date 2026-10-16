package edu.csuci.tweeter.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The home feed, the screen the app opens to. It is empty until the home feed story is built.
 *
 * The feed shows every post from `PostRepository.getPosts()`, newest first, laid out as in the
 * designs. See `AppContainer` for how a ViewModel gets the repository.
 *
 * @param modifier Applied to the whole screen.
 */
@Composable
fun FeedScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize())
}
