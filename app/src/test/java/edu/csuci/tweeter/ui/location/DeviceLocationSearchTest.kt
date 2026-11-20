@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.location

import edu.csuci.tweeter.location.GeoPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val DEVICE_POINT = GeoPoint(latitude = 34.16, longitude = -119.04)

class DeviceLocationSearchTest {
    private var nextFix = CompletableDeferred<GeoPoint?>()
    private val pickedPoints = mutableListOf<GeoPoint>()
    private var searchCount = 0

    @Test
    fun start_whileWaitingForFix_isLocating() = runTest {
        val search = deviceLocationSearch()

        search.start(pickedPoints::add)
        runCurrent()

        assertTrue(search.isLocating)
    }

    @Test
    fun start_fixFound_picksDevicePoint() = runTest {
        val search = deviceLocationSearch()
        search.start(pickedPoints::add)

        nextFix.complete(DEVICE_POINT)
        runCurrent()

        assertEquals(listOf(DEVICE_POINT), pickedPoints)
        assertFalse(search.isLocating)
    }

    @Test
    fun start_noFix_saysSoAndPicksNothing() = runTest {
        val search = deviceLocationSearch()
        search.start(pickedPoints::add)

        nextFix.complete(null)
        runCurrent()

        assertTrue(search.hasNoFix)
        assertFalse(search.isLocating)
        assertEquals(emptyList<GeoPoint>(), pickedPoints)
    }

    @Test
    fun start_afterNoFix_stopsSayingSoWhileLocating() = runTest {
        val search = deviceLocationSearchThatFoundNoFix()

        search.start(pickedPoints::add)
        runCurrent()

        assertFalse(search.hasNoFix)
        assertTrue(search.isLocating)
    }

    @Test
    fun start_whileLocating_startsNoOtherSearch() = runTest {
        val search = deviceLocationSearch()
        search.start(pickedPoints::add)

        search.start(pickedPoints::add)
        runCurrent()

        assertEquals(1, searchCount)
    }

    @Test
    fun stop_whileLocating_dropsFixThatComesLater() = runTest {
        val search = deviceLocationSearch()
        search.start(pickedPoints::add)
        runCurrent()

        search.stop()
        nextFix.complete(DEVICE_POINT)
        runCurrent()

        assertEquals(emptyList<GeoPoint>(), pickedPoints)
        assertFalse(search.isLocating)
    }

    @Test
    fun stop_beforeSearchRuns_searchesNothing() = runTest {
        val search = deviceLocationSearch()
        search.start(pickedPoints::add)

        search.stop()
        runCurrent()

        assertEquals(0, searchCount)
        assertFalse(search.isLocating)
    }

    @Test
    fun start_afterStop_searchesAgain() = runTest {
        val search = deviceLocationSearch()
        search.start(pickedPoints::add)
        runCurrent()
        search.stop()

        search.start(pickedPoints::add)
        nextFix.complete(DEVICE_POINT)
        runCurrent()

        assertEquals(2, searchCount)
        assertEquals(listOf(DEVICE_POINT), pickedPoints)
    }

    @Test
    fun stop_afterNoFix_stopsSayingSo() = runTest {
        val search = deviceLocationSearchThatFoundNoFix()

        search.stop()

        assertFalse(search.hasNoFix)
    }

    // Searches in the background scope, which ends with the test, since some fixes never come.
    private fun TestScope.deviceLocationSearch() = DeviceLocationSearch(backgroundScope) {
        searchCount++
        nextFix.await()
    }

    private fun TestScope.deviceLocationSearchThatFoundNoFix(): DeviceLocationSearch {
        val search = deviceLocationSearch()
        search.start(pickedPoints::add)
        nextFix.complete(null)
        runCurrent()
        nextFix = CompletableDeferred()
        return search
    }
}
