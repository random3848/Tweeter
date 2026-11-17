package edu.csuci.tweeter.data

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialExecutorTest {
    @Test
    fun serialExecutor_slowTaskThenFastTask_runsThemInOrderWithoutOverlap() {
        val executor = Dispatchers.Default.serialExecutor()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val bothDone = CountDownLatch(TASK_COUNT)

        executor.execute {
            events += "slow started"
            Thread.sleep(SLOW_TASK_MILLIS)
            events += "slow finished"
            bothDone.countDown()
        }
        executor.execute {
            events += "fast started"
            events += "fast finished"
            bothDone.countDown()
        }

        assertTrue(bothDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(
            listOf("slow started", "slow finished", "fast started", "fast finished"),
            events
        )
    }

    private companion object {
        const val TASK_COUNT = 2
        const val SLOW_TASK_MILLIS = 200L
        const val TIMEOUT_SECONDS = 5L
    }
}
