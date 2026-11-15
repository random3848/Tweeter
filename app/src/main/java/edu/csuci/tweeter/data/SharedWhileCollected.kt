package edu.csuci.tweeter.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/**
 * Returns one flow every collector shares, running this flow in [scope] once while anyone
 * collects it.
 *
 * A new collector gets the latest value right away. When the last collector stops, this flow is
 * stopped and its value dropped at once, so nothing is kept for later.
 */
internal fun <T> Flow<T>.sharedWhileCollected(scope: CoroutineScope): SharedFlow<T> = shareIn(
    scope,
    SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
    replay = 1
)
