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
        val choice = LocationChooser.chooseBest(exif, accurateDevice, manual, capturedInApp = true)
        assertEquals(manual, choice.fix)
    }

    @Test
    fun chooseBest_inAppCaptureAccurateFix_usesDevice() {
        val choice = LocationChooser.chooseBest(exif, accurateDevice, null, capturedInApp = true)
        assertEquals(accurateDevice, choice.fix)
    }

    @Test
    fun chooseBest_inAppCaptureCoarseFix_usesExif() {
        val choice = LocationChooser.chooseBest(exif, coarseDevice, null, capturedInApp = true)
        assertEquals(exif, choice.fix)
    }

    @Test
    fun chooseBest_inAppCaptureCoarseFixNoExif_usesDevice() {
        val choice = LocationChooser.chooseBest(null, coarseDevice, null, capturedInApp = true)
        assertEquals(coarseDevice, choice.fix)
    }

    @Test
    fun chooseBest_importedWithExif_ignoresDevice() {
        val choice = LocationChooser.chooseBest(exif, accurateDevice, null, capturedInApp = false)
        assertEquals(exif, choice.fix)
    }

    @Test
    fun chooseBest_importedWithoutExif_needsManualPin() {
        val choice = LocationChooser.chooseBest(null, accurateDevice, null, capturedInApp = false)
        assertNull(choice.fix)
    }

    @Test
    fun chooseBest_nothingAvailable_needsManualPin() {
        val choice = LocationChooser.chooseBest(null, null, null, capturedInApp = true)
        assertNull(choice.fix)
    }

    @Test
    fun haversineMeters_samePoint_isZero() {
        assertEquals(0.0, LocationChooser.haversineMeters(exif.point, exif.point), 1e-9)
    }

    @Test
    fun haversineMeters_oneThousandthDegreeLatitude_isAbout111Meters() {
        val a = GeoPoint(34.0, -119.0)
        val b = GeoPoint(34.001, -119.0)
        assertEquals(111.2, LocationChooser.haversineMeters(a, b), 0.5)
    }
}
