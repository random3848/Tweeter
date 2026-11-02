package edu.csuci.tweeter.ui.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which post is selected on Map, where its pin stands out and its photo and details show below
 * the map.
 *
 * The app shares one in [edu.csuci.tweeter.AppContainer], so another screen can show a post on
 * Map by selecting it before it opens Map, as Feed does.
 */
class MapSelection {
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
