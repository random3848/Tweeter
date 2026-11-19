package edu.csuci.tweeter.notification

import edu.csuci.tweeter.data.FakeNumberStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW_MILLIS = 1_700_000_000_000L

class NotificationPermissionRecordTest {
    private val numbers = FakeNumberStore()
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW_MILLIS), ZoneOffset.UTC)

    @Test
    fun wasRequested_neverRequested_isFalse() = runTest {
        assertFalse(NotificationPermissionRecord(numbers, clock).wasRequested())
    }

    @Test
    fun recordRequest_thenWasRequested_isTrue() = runTest {
        NotificationPermissionRecord(numbers, clock).recordRequest()

        assertTrue(NotificationPermissionRecord(numbers, clock).wasRequested())
    }
}
