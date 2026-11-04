package edu.csuci.tweeter.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedWhileCollectedTest {
    @Test
    fun sharedWhileCollected_twoCollectors_startsSourceOnce() = runTest {
        var starts = 0
        val source = flow {
            starts++
            emit(VALUE)
            awaitCancellation()
        }
        val shared = source.sharedWhileCollected(backgroundScope)

        backgroundScope.launch { shared.collect {} }
        backgroundScope.launch { shared.collect {} }
        runCurrent()

        assertEquals(1, starts)
    }

    @Test
    fun sharedWhileCollected_collectAgainAfterStop_restartsSourceWithoutReplay() = runTest {
        var starts = 0
        val source = flow {
            emit(++starts)
            awaitCancellation()
        }
        val shared = source.sharedWhileCollected(backgroundScope)
        shared.first()
        runCurrent()

        val second = shared.first()

        assertEquals(2, second)
    }

    private companion object {
        const val VALUE = 1
    }
}
