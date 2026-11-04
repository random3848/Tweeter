@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val INITIAL = 0
private const val READ = 1

class WhileAppVisibleTest {
    private val isAppVisible = MutableStateFlow(true)

    // How many times the shared flow started reading, and how many of those reads are running.
    private var reads = 0
    private var runningReads = 0

    @Test
    fun stateIn_lastCollectorLeavesWhileAppVisible_keepsReading() = runTest {
        val state = sharedReads()
        val screen = backgroundScope.launch { state.collect() }
        runCurrent()

        screen.cancel()
        backgroundScope.launch { state.collect() }
        runCurrent()

        assertEquals(1 to 1, reads to runningReads)
    }

    @Test
    fun stateIn_appGoesToBackground_stopsReadingAndKeepsLastValue() = runTest {
        val state = sharedReads()
        runCurrent()

        isAppVisible.value = false
        runCurrent()

        assertEquals(0 to READ, runningReads to state.value)
    }

    @Test
    fun stateIn_appComesBack_readsAgain() = runTest {
        val state = sharedReads()
        runCurrent()
        isAppVisible.value = false
        runCurrent()

        isAppVisible.value = true
        runCurrent()

        assertEquals(2 to 1, reads to runningReads)
    }

    @Test
    fun stateIn_appStartsInBackground_waitsToRead() = runTest {
        isAppVisible.value = false

        val state = sharedReads()
        runCurrent()

        assertEquals(0 to INITIAL, reads to state.value)
    }

    // A flow that counts its reads, shared the way a ViewModel shares what its screen shows.
    private fun TestScope.sharedReads() =
        countedReads().stateIn(backgroundScope, WhileAppVisible(isAppVisible), INITIAL)

    private fun countedReads(): Flow<Int> = flow {
        reads++
        runningReads++
        try {
            emit(READ)
            awaitCancellation()
        } finally {
            runningReads--
        }
    }
}
