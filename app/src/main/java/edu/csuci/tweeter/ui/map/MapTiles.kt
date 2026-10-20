package edu.csuci.tweeter.ui.map

import android.app.Application
import android.content.pm.PackageManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceKind
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceProvider
import org.maplibre.compose.resource.MapResourceRequest

// Where OpenStreetMap's operators can find who runs the app.
private const val PROJECT_URL = "https://github.com/random3848/Tweeter"

// Tiles download at most this many at a time, as many connections as a browser opens to one
// server. The rest wait.
internal const val MAX_PARALLEL_DOWNLOADS = 6

private const val TIMEOUT_MILLIS = 15_000

private const val HTTP_TOO_MANY_REQUESTS = 429

// How long to keep a tile whose response doesn't say. OpenStreetMap asks for at least 7 days.
internal val DEFAULT_TILE_LIFETIME = 7.days

// A failed download is tried again after this long, doubling with each failure up to
// MAX_RETRY_DELAY.
private val FIRST_RETRY_DELAY = 1.seconds
private val MAX_RETRY_DELAY = 16.seconds

private val MAX_AGE_DIRECTIVE = Regex("""max-age=(\d+)""", RegexOption.IGNORE_CASE)

/**
 * Makes the map download its tiles itself, naming the app in the User-Agent of every tile request:
 * `Tweeter/<version> (+<project URL>)`.
 *
 * OpenStreetMap's tile servers block requests that carry a map library's default User-Agent
 * (https://operations.osmfoundation.org/policies/tiles/). maplibre-compose 0.20.0 sends its
 * default even when its request interceptor supplies another one, so the app loads the tiles. The
 * map still caches them, and revalidates them once they expire.
 *
 * Call this once, from [Application.onCreate], before any map is shown.
 *
 * @param application The app, for its version.
 */
fun configureMapTiles(application: Application) {
    val versionName = application.packageManager
        .getPackageInfo(application.packageName, PackageManager.PackageInfoFlags.of(0))
        .versionName
    val tileProvider = TileProvider(userAgent = "Tweeter/$versionName (+$PROJECT_URL)")
    DefaultMapRuntime.configure {
        resourceProvider = tileProvider
    }
}

/**
 * Downloads every map tile over HTTP as [userAgent], with the validators of the cached copy.
 *
 * At most [MAX_PARALLEL_DOWNLOADS] tiles download at a time. When the map no longer needs a tile,
 * its download stops, whether it is still waiting or already running.
 *
 * The map doesn't retry a tile that the app failed to load, so downloads that can succeed later
 * (no connection, a server error, too many requests) are retried here while the map still needs
 * the tile. Without this, tiles that failed while offline would stay missing once back online.
 */
internal class TileProvider(private val userAgent: String) : MapResourceProvider {
    private val downloads = Semaphore(MAX_PARALLEL_DOWNLOADS)

    override fun accepts(request: MapResourceRequest): Boolean =
        request.kind == MapResourceKind.Tile

    override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad =
        download(request.url, request.priorEtag, request.priorModified)

    /**
     * Downloads the tile at [url], trying again after failures that can pass, and otherwise
     * reports why it couldn't. Cancelling the call stops the download.
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
        while (true) {
            val load = downloads.withPermit { downloadOnce(url, priorEtag, priorModified) }
            val failed = load as? MapResourceLoad.Failed ?: return load
            delay(readRetryDelay(failed, backoff) ?: return load)
            backoff = (backoff * 2).coerceAtMost(MAX_RETRY_DELAY)
        }
    }

    /** Makes one attempt at downloading the tile at [url]. */
    private suspend fun downloadOnce(
        url: String,
        priorEtag: String?,
        priorModified: Instant?
    ): MapResourceLoad = coroutineScope {
        val connection = URL(url).openConnection() as HttpURLConnection
        val response = async(Dispatchers.IO) {
            exchange(connection, priorEtag, priorModified)
        }
        try {
            response.await()
        } catch (cancelled: CancellationException) {
            // Closing the connection ends the blocked request, so the transfer stops now.
            connection.disconnect()
            throw cancelled
        }
    }

    /**
     * How long to wait before trying again after [failed]: [backoff], or as long as the server
     * asked. Null when trying again won't help.
     */
    private fun readRetryDelay(failed: MapResourceLoad.Failed, backoff: Duration): Duration? =
        when (failed.reason) {
            MapResourceError.Connection, MapResourceError.Server -> backoff

            MapResourceError.RateLimit -> {
                val untilRetryAfter = failed.retryAfter?.let { it - Clock.System.now() }
                untilRetryAfter?.coerceAtLeast(Duration.ZERO) ?: backoff
            }

            else -> null
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
        readResponse(connection)
    } catch (error: IOException) {
        MapResourceLoad.Failed(MapResourceError.Connection, error.toString())
    } finally {
        connection.disconnect()
    }

    /** Turns the response to [connection] into what the map's cache expects. */
    private fun readResponse(connection: HttpURLConnection): MapResourceLoad {
        val status = connection.responseCode
        val cacheControl = connection.getHeaderField("Cache-Control").orEmpty()
        val modified = connection.lastModified.takeIf { it > 0 }
            ?.let { Instant.fromEpochMilliseconds(it) }
        val expires = readExpiry(connection, cacheControl)
        return when (status) {
            HttpURLConnection.HTTP_OK -> {
                MapResourceLoad.Bytes(
                    bytes = connection.inputStream.use { it.readBytes() },
                    etag = connection.getHeaderField("ETag"),
                    mustRevalidate = cacheControl.contains("must-revalidate", ignoreCase = true) ||
                        cacheControl.contains("no-cache", ignoreCase = true),
                    modified = modified,
                    expires = expires
                )
            }

            HttpURLConnection.HTTP_NOT_MODIFIED -> MapResourceLoad.NotModified(modified, expires)

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
     * When the response to [connection] stops being fresh: now for `no-cache`, else its max-age,
     * else its Expires, else [DEFAULT_TILE_LIFETIME] from now.
     */
    private fun readExpiry(connection: HttpURLConnection, cacheControl: String): Instant {
        val now = Clock.System.now()
        val maxAge = MAX_AGE_DIRECTIVE.find(cacheControl)?.groupValues?.get(1)?.toLongOrNull()
        return when {
            cacheControl.contains("no-cache", ignoreCase = true) -> now
            maxAge != null -> now + maxAge.seconds
            connection.expiration > 0 -> Instant.fromEpochMilliseconds(connection.expiration)
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
}
