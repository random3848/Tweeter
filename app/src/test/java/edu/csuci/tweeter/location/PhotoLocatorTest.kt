@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.location

import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

// What the tests' sources are asked about; PhotoLocator only passes it on.
private const val PHOTO = "photo.jpg"

private const val EXIF_READ_MILLIS = 1_000L
private const val DEVICE_FIX_MILLIS = 2_000L

class PhotoLocatorTest {
    private val exif = LocationFix(GeoPoint(34.1614, -119.0434), LocationSource.EXIF)
    private val accurateDevice =
        LocationFix(GeoPoint(34.1615, -119.0435), LocationSource.DEVICE, accuracyMeters = 8f)

    @Test
    fun locate_capturedInApp_readsExifWhileWaitingForDevice() = runTest {
        val locator = slowLocator()

        locator.locate(PHOTO, capturedInApp = true)

        assertEquals(DEVICE_FIX_MILLIS, testScheduler.currentTime)
    }

    @Test
    fun locate_capturedInApp_choosesFromBothSources() = runTest {
        val locator = slowLocator()

        val location = locator.locate(PHOTO, capturedInApp = true)

        assertEquals(accurateDevice, location)
    }

    @Test
    fun locate_imported_neverAsksDevice() = runTest {
        val locator = PhotoLocator<String>(
            readExif = { exif },
            currentDeviceFix = { throw AssertionError("Asked the device about an imported photo") },
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val location = locator.locate(PHOTO, capturedInApp = false)

        assertEquals(exif, location)
    }

    @Test
    fun locate_readsExifOnIoDispatcher() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        var exifReadOn: ContinuationInterceptor? = null
        val locator = PhotoLocator<String>(
            readExif = {
                exifReadOn = currentCoroutineContext()[ContinuationInterceptor]
                exif
            },
            currentDeviceFix = { null },
            ioDispatcher = ioDispatcher
        )

        locator.locate(PHOTO, capturedInApp = false)

        assertSame(ioDispatcher, exifReadOn)
    }

    private fun TestScope.slowLocator(): PhotoLocator<String> = PhotoLocator(
        readExif = {
            delay(EXIF_READ_MILLIS)
            exif
        },
        currentDeviceFix = {
            delay(DEVICE_FIX_MILLIS)
            accurateDevice
        },
        ioDispatcher = StandardTestDispatcher(testScheduler)
    )
}
