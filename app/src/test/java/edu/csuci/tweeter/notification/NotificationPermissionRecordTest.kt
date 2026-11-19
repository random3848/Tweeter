package edu.csuci.tweeter.notification

import edu.csuci.tweeter.data.FakeNumberStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionRecordTest {
    private val numbers = FakeNumberStore()

    @Test
    fun wasRequested_neverRequested_isFalse() = runTest {
        assertFalse(NotificationPermissionRecord(numbers).wasRequested())
    }

    @Test
    fun recordRequest_thenWasRequested_isTrue() = runTest {
        NotificationPermissionRecord(numbers).recordRequest()

        assertTrue(NotificationPermissionRecord(numbers).wasRequested())
    }
}
