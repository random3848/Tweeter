@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.data

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val TAG = "RetryWithBackoffTest"
private const val VALUE = 7

class RetryWithBackoffTest {
    // When each collection of [listener] started, in virtual time.
    private val attemptTimes = mutableListOf<Long>()

    // Every value resets the backoff, as values only a working listener emits do.
    private val everyValueResets: (Int) -> Boolean = { true }

    // No value resets the backoff, as values a listener can emit before it fails.
    private val noValueResets: (Int) -> Boolean = { false }

    @Test
    fun retryWithBackoff_failsThenRecovers_emitsAfterFirstDelay() = runTest {
        val values = listener(failures = 1).retryWithBackoff(TAG, everyValueResets).toList()

        assertEquals(listOf(VALUE), values)
        assertEquals(listOf(0L, FIRST_RETRY_DELAY_MILLIS), attemptTimes)
    }

    @Test
    fun retryWithBackoff_keepsFailing_doublesDelayUpToMax() = runTest {
        val doublings = listOf(1L, 2L, 4L, 8L, 16L, 32L).map { it * FIRST_RETRY_DELAY_MILLIS }
        val expectedDelays = doublings + listOf(MAX_RETRY_DELAY_MILLIS, MAX_RETRY_DELAY_MILLIS)
        backgroundScope.launch {
            alwaysFailingListener().retryWithBackoff(TAG, everyValueResets).collect()
        }

        advanceTimeBy(expectedDelays.sum() + 1)

        assertEquals(expectedDelays, attemptTimes.zipWithNext { start, next -> next - start })
    }

    @Test
    fun retryWithBackoff_failsAfterValue_waitsFirstDelayAgain() = runTest {
        val upstream = flow {
            attemptTimes += currentTime
            when (attemptTimes.size) {
                1 -> throw IOException("Listener failed")
                2 -> emitThenFail()
                else -> emit(VALUE)
            }
        }

        upstream.retryWithBackoff(TAG, everyValueResets).toList()

        val expectedTimes = listOf(0L, FIRST_RETRY_DELAY_MILLIS, 2 * FIRST_RETRY_DELAY_MILLIS)
        assertEquals(expectedTimes, attemptTimes)
    }

    @Test
    fun retryWithBackoff_failsAfterValueThatDoesNotReset_keepsDoubling() = runTest {
        // Emits a value each time, then fails, as a listener does that the server rejects after
        // its snapshot from the cache.
        val upstream = flow {
            attemptTimes += currentTime
            emitThenFail()
        }
        backgroundScope.launch { upstream.retryWithBackoff(TAG, noValueResets).collect() }

        advanceTimeBy(3 * FIRST_RETRY_DELAY_MILLIS + 1)

        val expectedDelays = listOf(FIRST_RETRY_DELAY_MILLIS, 2 * FIRST_RETRY_DELAY_MILLIS)
        assertEquals(expectedDelays, attemptTimes.zipWithNext { start, next -> next - start })
    }

    @Test
    fun retryWithBackoff_cancelled_stopsRetrying() = runTest {
        val collector = launch {
            alwaysFailingListener().retryWithBackoff(TAG, everyValueResets).collect()
        }
        advanceTimeBy(FIRST_RETRY_DELAY_MILLIS + 1)
        val attemptsBeforeCancel = attemptTimes.size

        collector.cancel()
        advanceUntilIdle()

        assertEquals(attemptsBeforeCancel, attemptTimes.size)
    }

    @Test
    fun retryWithBackoff_error_rethrowsWithoutRetrying() = runTest {
        val upstream = flow<Int> {
            attemptTimes += currentTime
            throw StackOverflowError("Not a failure to retry")
        }

        val error = runCatching {
            upstream.retryWithBackoff(TAG, everyValueResets).toList()
        }.exceptionOrNull()

        assertEquals(StackOverflowError::class, error?.let { it::class })
        assertEquals(1, attemptTimes.size)
    }

    // A listener whose first [failures] collections fail; the next one emits [VALUE].
    private fun TestScope.listener(failures: Int): Flow<Int> = flow {
        attemptTimes += currentTime
        if (attemptTimes.size <= failures) {
            throw IOException("Listener failed")
        }
        emit(VALUE)
    }

    private fun TestScope.alwaysFailingListener(): Flow<Int> = listener(failures = Int.MAX_VALUE)

    private suspend fun FlowCollector<Int>.emitThenFail() {
        emit(VALUE)
        throw IOException("Listener failed after a value")
    }
}
