@file:OptIn(ExperimentalTime::class)

package edu.csuci.tweeter.ui.map

import edu.csuci.tweeter.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceKind
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceProvider
import org.maplibre.compose.resource.MapResourceRequest

// The tile server of assets/openstreetmap-style.json. The app downloads only this server's tiles;
// the map downloads everything else itself, with its own User-Agent.
private const val OPENSTREETMAP_TILE_SERVER = "https://tile.openstreetmap.org/"

// Where OpenStreetMap's operators can find who runs the app.
private const val PROJECT_URL = "https://github.com/random3848/Tweeter"

// Tiles download at most this many at a time, as many connections as a browser opens to one
// server. The rest wait.
internal const val MAX_PARALLEL_DOWNLOADS = 6

// A tile is requested at most this many times in a row before its failure goes to the map.
internal const val MAX_DOWNLOAD_ATTEMPTS = 5

private const val TIMEOUT_MILLIS = 15_000

private const val HTTP_TOO_MANY_REQUESTS = 429

// How long to keep a tile whose response doesn't say. OpenStreetMap asks for at least 7 days.
internal val DEFAULT_TILE_LIFETIME = 7.days

// A failed download is tried again after FIRST_RETRY_DELAY, doubling with each failure, or later
// if the server asks for that with Retry-After. A rate limit without Retry-After waits at least
// RATE_LIMIT_RETRY_DELAY, as MapLibre's own downloader does. A wait longer than MAX_RETRY_DELAY
// isn't waited out: the failure goes to the map instead.
private val FIRST_RETRY_DELAY = 1.seconds
internal val RATE_LIMIT_RETRY_DELAY = 5.seconds
internal val MAX_RETRY_DELAY = 1.minutes

private val MAX_AGE_DIRECTIVE = Regex("""max-age=(\d+)""", RegexOption.IGNORE_CASE)

// The Cache-Control directives that forbid using a stored response without asking the server.
private val NO_CACHE_DIRECTIVES = Regex("no-cache|no-store", RegexOption.IGNORE_CASE)

/**
 * Makes the map download its tiles itself, naming the app in the User-Agent of every tile request:
 * `Tweeter/<version> (+<project URL>)`.
 *
 * OpenStreetMap's tile servers block requests that carry a map library's default User-Agent
 * (https://operations.osmfoundation.org/policies/tiles/). maplibre-compose 0.20.0 sends its
 * default even when its request interceptor supplies another one, so the app loads the tiles. The
 * map still caches them, and revalidates them once they expire.
 *
 * Call this once, from [android.app.Application.onCreate], before any map is shown.
 */
fun configureMapTiles() {
    val userAgent = "Tweeter/${BuildConfig.VERSION_NAME} (+$PROJECT_URL)"
    DefaultMapRuntime.configure {
        resourceProvider = TileProvider(userAgent)
    }
}

/**
 * Downloads OpenStreetMap's tiles over HTTP as [userAgent], with the validators of the cached
 * copy.
 *
 * At most [MAX_PARALLEL_DOWNLOADS] tiles download at a time. When the map no longer needs a tile,
 * its download stops, whether it is still waiting or already running.
 *
 * The map doesn't retry a tile that the app failed to load, so downloads that can succeed later
 * (no connection, a server error, too many requests) are retried here while the map still needs
 * the tile, up to [MAX_DOWNLOAD_ATTEMPTS] attempts in all. Without this, a tile that failed while
 * the connection dropped for a moment would stay missing. A tile whose last attempt fails stays
 * missing until the map asks for it again, such as when it scrolls back into view.
 */
internal class TileProvider(private val userAgent: String) : MapResourceProvider {
    private val downloads = Semaphore(MAX_PARALLEL_DOWNLOADS)

    override fun accepts(request: MapResourceRequest): Boolean =
        request.kind == MapResourceKind.Tile && request.url.startsWith(OPENSTREETMAP_TILE_SERVER)

    // The map hands the app only loads that may use the network (its cache answers cache-only
    // ones), and applies the storage policy itself when it caches the result. Byte ranges come
    // only from PMTiles sources. So the URL and the cached copy's validators are all a tile needs.
    override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad =
        download(request.url, request.priorEtag, request.priorModified)

    /**
     * Downloads the tile at [url], trying again after failures that can pass, up to
     * [MAX_DOWNLOAD_ATTEMPTS] attempts in all, and otherwise reports why it couldn't. Cancelling
     * the call stops the download.
     *
     * @param priorEtag The ETag of the cached copy, if any; the server can then answer "not
     *     modified".
     * @param priorModified When the cached copy was last modified, if known.
     */
    suspend fun download(
        url: String,
        priorEtag: String?,
        priorModified: Instant?
    ): MapResourceLoad {
        var backoff = FIRST_RETRY_DELAY
        repeat(MAX_DOWNLOAD_ATTEMPTS - 1) {
            val load = downloadOnce(url, priorEtag, priorModified)
            val failed = load as? MapResourceLoad.Failed ?: return load
            delay(readRetryDelay(failed, backoff) ?: return load)
            backoff *= 2
        }
        return downloadOnce(url, priorEtag, priorModified)
    }

    /** Makes one attempt at downloading the tile at [url], once a download may start. */
    private suspend fun downloadOnce(
        url: String,
        priorEtag: String?,
        priorModified: Instant?
    ): MapResourceLoad = downloads.withPermit {
        withContext(Dispatchers.IO) {
            val connection = try {
                URL(url).openConnection() as HttpURLConnection
            } catch (error: IOException) {
                // A URL that can't be opened won't open later either.
                return@withContext MapResourceLoad.Failed(MapResourceError.Other, error.toString())
            }
            val response = async { exchange(connection, priorEtag, priorModified) }
            try {
                response.await()
            } catch (cancelled: CancellationException) {
                // Closing the connection ends the blocked request, so the transfer stops now.
                connection.disconnect()
                throw cancelled
            }
        }
    }

    /**
     * How long to wait before trying again after [failed]: [backoff], or longer if the server
     * asked for that with Retry-After, and at least [RATE_LIMIT_RETRY_DELAY] after a rate limit
     * without Retry-After. Null when trying again won't help, or would take a wait longer than
     * [MAX_RETRY_DELAY].
     */
    private fun readRetryDelay(failed: MapResourceLoad.Failed, backoff: Duration): Duration? {
        val untilRetryAfter = failed.retryAfter?.let { it - Clock.System.now() }
        val asked = when (failed.reason) {
            MapResourceError.Connection, MapResourceError.Server -> untilRetryAfter
            MapResourceError.RateLimit -> untilRetryAfter ?: RATE_LIMIT_RETRY_DELAY
            else -> return null
        }
        return maxOf(backoff, asked ?: Duration.ZERO).takeIf { it <= MAX_RETRY_DELAY }
    }

    /** Sends the request on [connection] and reads the response, blocking until both are done. */
    private fun exchange(
        connection: HttpURLConnection,
        priorEtag: String?,
        priorModified: Instant?
    ): MapResourceLoad = try {
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.setRequestProperty("User-Agent", userAgent)
        priorEtag?.let { connection.setRequestProperty("If-None-Match", it) }
        priorModified?.let { connection.ifModifiedSince = it.toEpochMilliseconds() }
        readResponse(connection, priorModified)
    } catch (error: IOException) {
        MapResourceLoad.Failed(classifyError(error), error.toString())
    } finally {
        connection.disconnect()
    }

    /**
     * Turns the response to [connection] into what the map's cache expects.
     *
     * @param priorModified When the cached copy was last modified, which a "not modified" response
     *     without Last-Modified keeps.
     */
    private fun readResponse(
        connection: HttpURLConnection,
        priorModified: Instant?
    ): MapResourceLoad {
        val status = connection.responseCode
        val cacheControl = connection.getHeaderField("Cache-Control").orEmpty()
        // The map can't be told not to store a response, so a no-store one is stored like a
        // no-cache one: expired, to be revalidated before every use.
        val noCache = NO_CACHE_DIRECTIVES.containsMatchIn(cacheControl)
        val modified = connection.lastModified.takeIf { it > 0 }
            ?.let { Instant.fromEpochMilliseconds(it) }
        val expires = if (noCache) Clock.System.now() else readExpiry(connection, cacheControl)
        return when (status) {
            HttpURLConnection.HTTP_OK -> {
                MapResourceLoad.Bytes(
                    bytes = connection.inputStream.use { it.readBytes() },
                    etag = connection.getHeaderField("ETag"),
                    mustRevalidate = noCache ||
                        cacheControl.contains("must-revalidate", ignoreCase = true),
                    modified = modified,
                    expires = expires
                )
            }

            HttpURLConnection.HTTP_NO_CONTENT -> MapResourceLoad.NoContent(modified, expires)

            HttpURLConnection.HTTP_NOT_MODIFIED -> {
                MapResourceLoad.NotModified(modified ?: priorModified, expires)
            }

            else -> {
                MapResourceLoad.Failed(
                    reason = classifyError(status),
                    message = "HTTP $status",
                    retryAfter = readRetryAfter(connection)
                )
            }
        }
    }

    /**
     * When the response to [connection] stops being fresh: once its max-age has passed, counting
     * the time it already spent in caches (Age), else at its Expires (already past when that
     * isn't a date, such as `0`), else [DEFAULT_TILE_LIFETIME] from now.
     */
    private fun readExpiry(connection: HttpURLConnection, cacheControl: String): Instant {
        val now = Clock.System.now()
        val maxAge = MAX_AGE_DIRECTIVE.find(cacheControl)?.groupValues?.get(1)?.toLongOrNull()
        return when {
            maxAge != null -> {
                val age = connection.getHeaderField("Age")?.trim()?.toLongOrNull() ?: 0L
                now + (maxAge - age).coerceAtLeast(0L).seconds
            }

            connection.getHeaderField("Expires") != null -> {
                Instant.fromEpochMilliseconds(connection.getHeaderFieldDate("Expires", 0L))
            }

            else -> now + DEFAULT_TILE_LIFETIME
        }
    }

    /** When the server allows the next request, from the Retry-After of [connection], if any. */
    private fun readRetryAfter(connection: HttpURLConnection): Instant? {
        val seconds = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
        val date = connection.getHeaderFieldDate("Retry-After", 0)
        return when {
            seconds != null -> Clock.System.now() + seconds.seconds
            date > 0 -> Instant.fromEpochMilliseconds(date)
            else -> null
        }
    }

    /** Classifies the failed HTTP [status] the way the map handles it. */
    private fun classifyError(status: Int): MapResourceError = when {
        status == HttpURLConnection.HTTP_NOT_FOUND -> MapResourceError.NotFound
        status == HTTP_TOO_MANY_REQUESTS -> MapResourceError.RateLimit
        status >= HttpURLConnection.HTTP_INTERNAL_ERROR -> MapResourceError.Server
        else -> MapResourceError.Other
    }

    /**
     * Classifies [error], which ended a request without a response, the way the map handles it:
     * [MapResourceError.Connection], which is retried, for a failure that can pass, else
     * [MapResourceError.Other].
     *
     * A timeout, a connection that couldn't be made or broke, and a host name that didn't resolve
     * can pass; the last is also how a request fails while the device is offline. TLS, protocol
     * and URL errors can't.
     */
    private fun classifyError(error: IOException): MapResourceError = when (error) {
        is SocketTimeoutException, is SocketException, is UnknownHostException -> {
            MapResourceError.Connection
        }

        else -> MapResourceError.Other
    }
}
