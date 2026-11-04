package edu.csuci.tweeter.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One screen's request for another to show a post, which the other screen clears once it has.
 *
 * The app shares one for each screen that can be asked, in [edu.csuci.tweeter.AppContainer]:
 * Feed asks Map to open a post's popup, and that popup asks Feed to scroll to the post. A request
 * is handled once, so opening the screen again doesn't repeat it.
 */
class ShowPostRequest {
    private val _postId = MutableStateFlow<String?>(null)

    /** The ID of the post to show, or null while nothing is requested. */
    val postId: StateFlow<String?> = _postId.asStateFlow()

    /** Asks for the post with [postId] to be shown, in place of any earlier request. */
    fun send(postId: String) {
        _postId.value = postId
    }

    /** Clears the request once the screen it was for has shown the post. */
    fun clear() {
        _postId.value = null
    }
}
