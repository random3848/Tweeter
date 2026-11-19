package edu.csuci.tweeter.location

import edu.csuci.tweeter.data.FakeNumberStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val CAMPUS = GeoPoint(latitude = 34.1626, longitude = -119.0434)
private val SOUTHERN_HEMISPHERE = GeoPoint(latitude = -33.8688, longitude = 151.2093)

class LastLocationStoreTest {
    private val numbers = FakeNumberStore()

    @Test
    fun lastLocation_nothingSaved_isNull() = runTest {
        assertNull(LastLocationStore(numbers).lastLocation())
    }

    @Test
    fun save_thenLastLocation_returnsSavedPoint() = runTest {
        LastLocationStore(numbers).save(CAMPUS)

        assertEquals(CAMPUS, LastLocationStore(numbers).lastLocation())
    }

    @Test
    fun save_negativeLatitudeAndPositiveLongitude_keepsSigns() = runTest {
        LastLocationStore(numbers).save(SOUTHERN_HEMISPHERE)

        assertEquals(SOUTHERN_HEMISPHERE, LastLocationStore(numbers).lastLocation())
    }

    @Test
    fun save_twice_keepsLatestPoint() = runTest {
        val store = LastLocationStore(numbers)
        store.save(CAMPUS)

        store.save(SOUTHERN_HEMISPHERE)

        assertEquals(SOUTHERN_HEMISPHERE, store.lastLocation())
    }
}
