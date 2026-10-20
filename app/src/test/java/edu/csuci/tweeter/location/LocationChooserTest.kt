package edu.csuci.tweeter.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationChooserTest {
    private val exif = LocationFix(GeoPoint(34.1614, -119.0434), LocationSource.EXIF)
    private val accurateDevice =
        LocationFix(GeoPoint(34.1615, -119.0435), LocationSource.DEVICE, accuracyMeters = 8f)
    private val coarseDevice =
        LocationFix(GeoPoint(34.1700, -119.0500), LocationSource.DEVICE, accuracyMeters = 500f)
    private val manual = LocationFix(GeoPoint(34.1620, -119.0440), LocationSource.MANUAL)

    @Test
    fun chooseBest_manualPin_alwaysWins() {
        assertEquals(
            manual,
            LocationChooser.chooseBest(exif, accurateDevice, manual, capturedInApp = true)
        )
        assertEquals(
            manual,
            LocationChooser.chooseBest(exif, accurateDevice, manual, capturedInApp = false)
        )
    }

    @Test
    fun chooseBest_inAppAccurateDevice_usesDevice() {
        assertEquals(
            accurateDevice,
            LocationChooser.chooseBest(exif, accurateDevice, null, capturedInApp = true)
        )
    }

    @Test
    fun chooseBest_inAppDeviceAtThreshold_usesDevice() {
        val device = accurateDevice.copy(
            accuracyMeters = LocationChooser.MAX_TRUSTED_DEVICE_ACCURACY_METERS
        )

        assertEquals(device, LocationChooser.chooseBest(exif, device, null, capturedInApp = true))
    }

    @Test
    fun chooseBest_inAppCoarseDevice_usesExif() {
        assertEquals(
            exif,
            LocationChooser.chooseBest(exif, coarseDevice, null, capturedInApp = true)
        )
    }

    @Test
    fun chooseBest_inAppCoarseDeviceNoExif_usesCoarseDevice() {
        assertEquals(
            coarseDevice,
            LocationChooser.chooseBest(null, coarseDevice, null, capturedInApp = true)
        )
    }

    @Test
    fun chooseBest_inAppDeviceWithoutAccuracy_prefersExif() {
        val device = accurateDevice.copy(accuracyMeters = null)

        assertEquals(exif, LocationChooser.chooseBest(exif, device, null, capturedInApp = true))
    }

    @Test
    fun chooseBest_importedPhoto_ignoresDevice() {
        assertEquals(
            exif,
            LocationChooser.chooseBest(exif, accurateDevice, null, capturedInApp = false)
        )
        assertNull(LocationChooser.chooseBest(null, accurateDevice, null, capturedInApp = false))
    }

    @Test
    fun chooseBest_nothingAvailable_returnsNull() {
        assertNull(LocationChooser.chooseBest(null, null, null, capturedInApp = true))
    }
}
