package edu.csuci.tweeter.notification

import edu.csuci.tweeter.data.FakeNumberStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val CHECK_NAME = "rareAnimals"
private const val OTHER_CHECK_NAME = "popularPosts"
private const val EARLIER_MILLIS = 1_000L
private const val LATER_MILLIS = 2_000L

class LastCheckStoreTest {
    private val numbers = FakeNumberStore()

    @Test
    fun lastCheckMillis_neverChecked_isNull() = runTest {
        assertNull(LastCheckStore(numbers, CHECK_NAME).lastCheckMillis())
    }

    @Test
    fun advanceTo_neverChecked_savesTime() = runTest {
        LastCheckStore(numbers, CHECK_NAME).advanceTo(EARLIER_MILLIS)

        assertEquals(EARLIER_MILLIS, LastCheckStore(numbers, CHECK_NAME).lastCheckMillis())
    }

    @Test
    fun advanceTo_laterTime_movesForward() = runTest {
        val store = LastCheckStore(numbers, CHECK_NAME)
        store.advanceTo(EARLIER_MILLIS)

        store.advanceTo(LATER_MILLIS)

        assertEquals(LATER_MILLIS, store.lastCheckMillis())
    }

    @Test
    fun advanceTo_earlierTime_keepsLaterTime() = runTest {
        val store = LastCheckStore(numbers, CHECK_NAME)
        store.advanceTo(LATER_MILLIS)

        store.advanceTo(EARLIER_MILLIS)

        assertEquals(LATER_MILLIS, store.lastCheckMillis())
    }

    @Test
    fun advanceTo_sameTime_keepsIt() = runTest {
        val store = LastCheckStore(numbers, CHECK_NAME)
        store.advanceTo(LATER_MILLIS)

        store.advanceTo(LATER_MILLIS)

        assertEquals(LATER_MILLIS, store.lastCheckMillis())
    }

    @Test
    fun advanceTo_oneCheck_leavesOtherChecksAlone() = runTest {
        LastCheckStore(numbers, CHECK_NAME).advanceTo(LATER_MILLIS)

        assertNull(LastCheckStore(numbers, OTHER_CHECK_NAME).lastCheckMillis())
    }

    @Test
    fun clear_afterCheck_forgetsTime() = runTest {
        val store = LastCheckStore(numbers, CHECK_NAME)
        store.advanceTo(LATER_MILLIS)

        store.clear()

        assertNull(store.lastCheckMillis())
    }

    @Test
    fun clear_oneCheck_leavesOtherChecksAlone() = runTest {
        LastCheckStore(numbers, OTHER_CHECK_NAME).advanceTo(LATER_MILLIS)

        LastCheckStore(numbers, CHECK_NAME).clear()

        assertEquals(LATER_MILLIS, LastCheckStore(numbers, OTHER_CHECK_NAME).lastCheckMillis())
    }
}
