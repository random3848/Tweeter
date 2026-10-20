@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package edu.csuci.tweeter.ui.map

import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlinx.coroutines.withTimeoutOrNull
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceLoad

private const val USER_AGENT = "Tweeter/test (+https://example.org)"

// Longer than any test waits, so a response delayed this long is one the test never receives.
private const val NEVER_SECONDS = 30L

class TileProviderTest {
    private val server = MockWebServer()
    private val provider = TileProvider(userAgent = USER_AGENT)

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun download_sendsUserAgent_returnsTileWithValidators() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body("tile")
                .addHeader("ETag", "\"v1\"")
                .addHeader("Last-Modified", "Mon, 19 Oct 2026 00:00:00 GMT")
                .build()
        )

        val load = provider.download(tileUrl(), priorEtag = null, priorModified = null)

        assertEquals(USER_AGENT, server.takeRequest().headers["User-Agent"])
        val tile = load as MapResourceLoad.Bytes
        assertEquals("tile", tile.bytes.decodeToString())
        assertEquals("\"v1\"", tile.etag)
        assertEquals(Instant.parse("2026-10-19T00:00:00Z"), tile.modified)
    }

    @Test
    fun download_cachedCopy_revalidatesAndReturnsNotModified() = runBlocking {
        val priorModified = Instant.parse("2026-10-19T00:00:00Z")
        server.enqueue(MockResponse.Builder().code(304).build())

        val load = provider.download(tileUrl(), priorEtag = "\"v1\"", priorModified = priorModified)

        val request = server.takeRequest()
        assertEquals("\"v1\"", request.headers["If-None-Match"])
        assertEquals("Mon, 19 Oct 2026 00:00:00 GMT", request.headers["If-Modified-Since"])
        // The response has no Last-Modified, so the cached copy keeps its own.
        assertEquals(priorModified, (load as MapResourceLoad.NotModified).modified)
    }

    @Test
    fun download_maxAgeAndExpires_expiresAfterMaxAge() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Cache-Control", "public, Max-Age=60")
                .addHeader("Expires", "Mon, 19 Oct 2026 00:00:00 GMT")
                .build()
        )
        val before = Clock.System.now()

        val tile = provider.download(tileUrl(), null, null) as MapResourceLoad.Bytes

        assertBetween(tile.expires, before + 60.seconds, Clock.System.now() + 60.seconds)
        assertFalse(tile.mustRevalidate)
    }

    @Test
    fun download_noCachingHeaders_keepsTileForDefaultLifetime() = runBlocking {
        server.enqueue(MockResponse.Builder().build())
        val before = Clock.System.now()

        val tile = provider.download(tileUrl(), null, null) as MapResourceLoad.Bytes

        assertBetween(
            tile.expires,
            before + DEFAULT_TILE_LIFETIME,
            Clock.System.now() + DEFAULT_TILE_LIFETIME
        )
    }

    @Test
    fun download_noCache_expiresNowAndMustRevalidate() = runBlocking {
        server.enqueue(
            MockResponse.Builder().addHeader("Cache-Control", "no-cache, max-age=600").build()
        )
        val before = Clock.System.now()

        val tile = provider.download(tileUrl(), null, null) as MapResourceLoad.Bytes

        assertBetween(tile.expires, before, Clock.System.now())
        assertTrue(tile.mustRevalidate)
    }

    @Test
    fun download_noStore_expiresNowAndMustRevalidate() = runBlocking {
        server.enqueue(MockResponse.Builder().addHeader("Cache-Control", "no-store").build())
        val before = Clock.System.now()

        val tile = provider.download(tileUrl(), null, null) as MapResourceLoad.Bytes

        assertBetween(tile.expires, before, Clock.System.now())
        assertTrue(tile.mustRevalidate)
    }

    @Test
    fun download_maxAgeAndAge_expiresWhenAgeReachesMaxAge() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Cache-Control", "max-age=600")
                .addHeader("Age", "100")
                .build()
        )
        val before = Clock.System.now()

        val tile = provider.download(tileUrl(), null, null) as MapResourceLoad.Bytes

        assertBetween(tile.expires, before + 500.seconds, Clock.System.now() + 500.seconds)
    }

    @Test
    fun download_invalidExpires_isAlreadyExpired() = runBlocking {
        server.enqueue(MockResponse.Builder().addHeader("Expires", "0").build())

        val tile = provider.download(tileUrl(), null, null) as MapResourceLoad.Bytes

        assertTrue("${tile.expires} is not in the past", tile.expires!! < Clock.System.now())
    }

    @Test
    fun download_noContent_returnsNoContent() = runBlocking {
        server.enqueue(MockResponse.Builder().code(204).build())

        val load = provider.download(tileUrl(), null, null)

        assertTrue(load is MapResourceLoad.NoContent)
    }

    @Test
    fun download_serverError_retriesUntilItSucceeds() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().body("tile").build())

        val load = provider.download(tileUrl(), null, null)

        assertTrue(load is MapResourceLoad.Bytes)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun download_tooManyRequests_retriesAfterRetryAfter() = runBlocking {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "2").build())
        server.enqueue(MockResponse.Builder().body("tile").build())
        val start = TimeSource.Monotonic.markNow()

        val load = provider.download(tileUrl(), null, null)

        assertTrue(load is MapResourceLoad.Bytes)
        // Longer than the first backoff, so the wait came from Retry-After.
        assertTrue(start.elapsedNow() >= 2.seconds)
    }

    @Test
    fun download_tooManyRequestsWithoutRetryAfter_waitsRateLimitDelay() = runTest {
        server.enqueue(MockResponse.Builder().code(429).build())
        server.enqueue(MockResponse.Builder().body("tile").build())
        val start = testTimeSource.markNow()

        val load = provider.download(tileUrl(), null, null)

        assertTrue(load is MapResourceLoad.Bytes)
        assertEquals(RATE_LIMIT_RETRY_DELAY, start.elapsedNow())
    }

    @Test
    fun download_serviceUnavailableWithRetryAfter_waitsAsAsked() = runTest {
        server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "10").build())
        server.enqueue(MockResponse.Builder().body("tile").build())
        val start = testTimeSource.markNow()

        val load = provider.download(tileUrl(), null, null)

        assertTrue(load is MapResourceLoad.Bytes)
        // Retry-After becomes a time on the real clock, which moves on a little before the wait.
        assertTrue(start.elapsedNow() in 9.seconds..10.seconds)
    }

    @Test
    fun download_retryAfterBeyondMaxDelay_reportsFailureWithoutRetrying() = runTest {
        val retryAfter = (MAX_RETRY_DELAY + 1.seconds).inWholeSeconds
        server.enqueue(
            MockResponse.Builder().code(503).addHeader("Retry-After", "$retryAfter").build()
        )
        server.enqueue(MockResponse.Builder().body("tile").build())

        val failed = provider.download(tileUrl(), null, null) as MapResourceLoad.Failed

        assertEquals(MapResourceError.Server, failed.reason)
        assertNotNull(failed.retryAfter)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun download_serverErrorEveryAttempt_reportsFailureAfterMaxAttempts() = runTest {
        repeat(MAX_DOWNLOAD_ATTEMPTS) { server.enqueue(MockResponse.Builder().code(503).build()) }
        // Served only to an attempt beyond the limit.
        server.enqueue(MockResponse.Builder().body("tile").build())

        val failed = provider.download(tileUrl(), null, null) as MapResourceLoad.Failed

        assertEquals(MapResourceError.Server, failed.reason)
        assertEquals(MAX_DOWNLOAD_ATTEMPTS, server.requestCount)
    }

    @Test
    fun download_notFound_reportsNotFoundWithoutRetrying() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())

        val failed = provider.download(tileUrl(), null, null) as MapResourceLoad.Failed

        assertEquals(MapResourceError.NotFound, failed.reason)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun download_tlsFails_reportsFailureWithoutRetrying() = runTest {
        // Answers in plain HTTP, so the TLS handshake fails, as on a network that intercepts HTTPS.
        ServerSocket(0).use { plainServer ->
            thread(isDaemon = true) {
                plainServer.accept().use { socket ->
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\n\r\n".toByteArray())
                    // Open until the client closes it, so the client reads the answer.
                    socket.getInputStream().readBytes()
                }
            }
            val url = "https://localhost:${plainServer.localPort}/16/11096/26143.png"
            val start = testTimeSource.markNow()

            val failed = provider.download(url, null, null) as MapResourceLoad.Failed

            assertEquals(MapResourceError.Other, failed.reason)
            assertEquals(Duration.ZERO, start.elapsedNow())
        }
    }

    @Test
    fun download_offlineThenOnline_loadsTile() = runBlocking {
        val url = tileUrl()
        val port = server.port
        server.close()

        val download = async(Dispatchers.Default) { provider.download(url, null, null) }
        delay(500)
        MockWebServer().use { revived ->
            revived.enqueue(MockResponse.Builder().body("tile").build())
            revived.start(port)

            assertTrue(download.await() is MapResourceLoad.Bytes)
        }
    }

    @Test
    fun download_overLimit_extraDownloadWaits() = runBlocking {
        val count = MAX_PARALLEL_DOWNLOADS + 1
        repeat(count) { server.enqueue(delayedResponse()) }

        val downloads = List(count) {
            launch(Dispatchers.Default) { provider.download(tileUrl(), null, null) }
        }

        repeat(MAX_PARALLEL_DOWNLOADS) {
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        }
        assertNull(server.takeRequest(1, TimeUnit.SECONDS))
        downloads.forEach { it.cancel() }
        downloads.joinAll()
    }

    @Test
    fun download_cancelledWhileTransferring_stopsPromptly() = runBlocking {
        server.enqueue(delayedResponse())
        val download = launch(Dispatchers.Default) { provider.download(tileUrl(), null, null) }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

        val stopped = withTimeoutOrNull(2.seconds) { download.cancelAndJoin() }

        assertNotNull("The download kept running after it was cancelled", stopped)
    }

    private fun tileUrl(): String = server.url("/16/11096/26143.png").toString()

    private fun delayedResponse(): MockResponse =
        MockResponse.Builder().headersDelay(NEVER_SECONDS, TimeUnit.SECONDS).build()

    private fun assertBetween(actual: Instant?, earliest: Instant, latest: Instant) {
        assertNotNull(actual)
        assertTrue("$actual is not between $earliest and $latest", actual!! in earliest..latest)
    }
}
