package edu.csuci.tweeter.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A post that one screen asks another to show.
 *
 * The app shares one for each screen that can be asked, in [edu.csuci.tweeter.AppContainer]:
 * Feed selects a post for Map before opening Map, which opens the post's popup, and that popup
 * selects the post for Feed before opening Home, which scrolls to it.
 */
class PostSelection {
    private val _postId = MutableStateFlow<String?>(null)

    /** The ID of the selected post, or null while none is selected. */
    val postId: StateFlow<String?> = _postId.asStateFlow()

    /** Selects the post with [postId] in place of any earlier selection. */
    fun select(postId: String) {
        _postId.value = postId
    }

    /** Clears the selection. */
    fun clear() {
        _postId.value = null
    }
}
