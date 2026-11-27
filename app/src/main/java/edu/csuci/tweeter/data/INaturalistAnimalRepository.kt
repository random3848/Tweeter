package edu.csuci.tweeter.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// iNaturalist's ID of the kingdom Animalia, so searches find animals and not plants or fungi.
private const val ANIMALIA_TAXON_ID = "1"

// Searches taxa by name, best matches first.
private const val AUTOCOMPLETE_PATH = "/v1/taxa/autocomplete"

// How many animals a search returns: as many as the animal field's suggestions show.
private const val MAX_SEARCH_RESULTS = "10"

// The language of the common names.
private const val LOCALE = "en"

private const val TIMEOUT_MILLIS = 10_000

// Reads iNaturalist's answers, which hold many fields the app doesn't use.
private val JSON = Json { ignoreUnknownKeys = true }

/**
 * An [AnimalRepository] that asks iNaturalist's API (https://api.inaturalist.org/v1/docs/) for
 * animals, by their English common names. It needs no account or key. iNaturalist asks apps to
 * send at most about one request a second, so callers search once the user stops typing.
 *
 * Searches find only species, so that everyone who agrees on a post's animal names the same one:
 * a post tagged with a family, such as "Hummingbirds", would count as disagreeing with one tagged
 * with a species in it. [getAnimal] also finds groups, which older posts may name.
 *
 * @param baseUrl Where the API is, without a trailing slash, such as
 *     `https://api.inaturalist.org`.
 * @param userAgent Names the app in every request, as iNaturalist asks.
 * @param ioDispatcher Where the blocking requests run.
 */
// iNaturalist's name starts with a one-letter word.
@Suppress("ktlint:android-style:camel-case")
class INaturalistAnimalRepository(
    private val baseUrl: String,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher
) : AnimalRepository {
    override suspend fun searchAnimals(query: String): List<String> {
        val text = query.trim()
        if (text.isEmpty()) {
            return emptyList()
        }

        val names = searchSpecies(text).map { it.postedName }
        return names.distinctBy { it.lowercase() }
    }

    /** Searches the animal's name, then reads the taxon that has exactly that name. */
    override suspend fun getAnimal(name: String): Animal? {
        val text = name.trim()
        if (text.isEmpty()) {
            return null
        }

        val match = searchTaxa(text).find { it.isNamed(text) } ?: return null
        return readTaxon(match.id).toAnimal()
    }

    private suspend fun searchSpecies(text: String): List<INaturalistTaxa.Taxon> =
        readTaxa(AUTOCOMPLETE_PATH, parameters = searchParameters(text) + ("rank" to "species"))

    // Finds groups, such as a genus or family, as well as species: older posts may name one.
    private suspend fun searchTaxa(text: String): List<INaturalistTaxa.Taxon> =
        readTaxa(AUTOCOMPLETE_PATH, parameters = searchParameters(text))

    private fun searchParameters(text: String): Map<String, String> = mapOf(
        "q" to text,
        "taxon_id" to ANIMALIA_TAXON_ID,
        "per_page" to MAX_SEARCH_RESULTS,
        "locale" to LOCALE
    )

    private suspend fun readTaxon(id: Int): INaturalistTaxa.Taxon {
        val path = "/v1/taxa/$id"
        val taxa = readTaxa(path, parameters = mapOf("locale" to LOCALE))
        return taxa.firstOrNull() ?: throw AnimalLookupException("Reading $path found no taxon")
    }

    /** The taxa iNaturalist answers [path] with, given the query [parameters]. */
    private suspend fun readTaxa(
        path: String,
        parameters: Map<String, String>
    ): List<INaturalistTaxa.Taxon> {
        val body = try {
            download(urlOf(path, parameters))
        } catch (error: IOException) {
            // A cancelled request fails too, as it is closed; that is no failure to report.
            currentCoroutineContext().ensureActive()
            throw AnimalLookupException("Reading $path from iNaturalist failed", error)
        }
        return try {
            JSON.decodeFromString<INaturalistTaxa>(body).results
        } catch (error: SerializationException) {
            throw AnimalLookupException("Reading $path from iNaturalist failed", error)
        }
    }

    private fun urlOf(path: String, parameters: Map<String, String>): URL {
        val query = parameters.entries.joinToString("&") { (name, value) ->
            name + "=" + URLEncoder.encode(value, Charsets.UTF_8)
        }
        return URL("$baseUrl$path?$query")
    }

    /**
     * The body of the response to a GET of [url]. Cancelling the call closes the connection, which
     * ends a request blocked on the network at once.
     *
     * @throws IOException If it can't be read, or the response isn't 200 OK.
     */
    private suspend fun download(url: URL): String = withContext(ioDispatcher) {
        val connection = url.openConnection() as HttpURLConnection
        val response = async { exchange(connection) }
        try {
            response.await()
        } catch (cancelled: CancellationException) {
            connection.disconnect()
            throw cancelled
        }
    }

    /** Sends the request on [connection] and reads the response, blocking until both are done. */
    private fun exchange(connection: HttpURLConnection): String = try {
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept", "application/json")
        readBody(connection)
    } finally {
        connection.disconnect()
    }

    private fun readBody(connection: HttpURLConnection): String {
        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP $status")
        }
        return connection.inputStream.use { it.readBytes().decodeToString() }
    }
}
