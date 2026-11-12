package edu.csuci.tweeter.data

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

/** How long [retryWithBackoff] waits before retrying after a first failure. */
internal const val FIRST_RETRY_DELAY_MILLIS = 1_000L

/** The longest [retryWithBackoff] waits before a retry, however many failures came before. */
internal const val MAX_RETRY_DELAY_MILLIS = 60_000L

/**
 * Collects this flow again whenever it fails with an exception, such as a snapshot listener that
 * stopped, logging each failure under [tag].
 *
 * It waits before each retry: [FIRST_RETRY_DELAY_MILLIS] after a first failure, twice as long
 * after each further failure in a row, up to [MAX_RETRY_DELAY_MILLIS], and the first delay again
 * once a value for which [resetsBackoff] is true has arrived. A value that can arrive before the
 * failure all the same, such as a listener's snapshot from its cache, shouldn't reset it.
 * Collectors keep what they received meanwhile, so a shared flow keeps its latest value.
 * Cancelling the collector stops it, and an [Error] isn't retried but thrown.
 */
fun <T> Flow<T>.retryWithBackoff(tag: String, resetsBackoff: (T) -> Boolean): Flow<T> = flow {
    val backoff = Backoff(tag)
    val retried = onEach { value ->
        if (resetsBackoff(value)) {
            backoff.reset()
        }
    }.retryWhen { cause, _ ->
        val isRetried = cause is Exception
        if (isRetried) {
            backoff.waitAfter(cause)
        }
        isRetried
    }
    emitAll(retried)
}

// The wait before the next retry, doubled after each failure in a row.
private class Backoff(private val tag: String) {
    private var delayMillis = FIRST_RETRY_DELAY_MILLIS

    fun reset() {
        delayMillis = FIRST_RETRY_DELAY_MILLIS
    }

    // Logs [failure], then waits before the retry it calls for.
    suspend fun waitAfter(failure: Throwable) {
        Log.w(tag, "Failed; retrying in $delayMillis ms", failure)
        delay(delayMillis)
        delayMillis = (delayMillis * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
    }
}
