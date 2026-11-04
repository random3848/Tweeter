package edu.csuci.tweeter.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Shares a ViewModel's state while the app is on screen, whether or not a screen collects it.
 *
 * A rotation recreates every screen, and their collectors with them; sharing only while collected
 * would stop each flow and start it again, reading every post again. In the background, sharing
 * stops, keeping the last value for when the app comes back.
 *
 * @param isAppVisible Whether the app is on screen, as it changes.
 */
class WhileAppVisible(private val isAppVisible: Flow<Boolean>) : SharingStarted {
    override fun command(subscriptionCount: StateFlow<Int>): Flow<SharingCommand> = isAppVisible
        .distinctUntilChanged()
        .map { isVisible -> if (isVisible) SharingCommand.START else SharingCommand.STOP }
}
