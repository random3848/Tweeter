package edu.csuci.tweeter.data

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val USER_AGENT = "Tweeter/test (+https://example.org)"

// iNaturalist's ID of the kingdom Animalia, which searches are limited to.
private const val ANIMALIA_TAXON_ID = "1"

private const val ISLAND_FOX_TAXON_ID = 42077

private const val HTTP_SERVER_ERROR = 500

// iNaturalist's name starts with a one-letter word.
@Suppress("ktlint:android-style:camel-case")
class INaturalistAnimalRepositoryTest {
    private val server = MockWebServer()

    // A thread of the test's own for the blocking requests, as Dispatchers.IO gives the app.
    private val ioDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private lateinit var repository: INaturalistAnimalRepository

    @Before
    fun startServer() {
        server.start()
        repository = INaturalistAnimalRepository(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            userAgent = USER_AGENT,
            ioDispatcher = ioDispatcher
        )
    }

    @After
    fun stopServer() {
        server.close()
        ioDispatcher.close()
    }

    @Test
    fun searchAnimals_searchesAnimalsByName() = runBlocking {
        server.enqueue(jsonResponse(fixture("autocomplete_island_fox.json")))

        repository.searchAnimals(" island fox ")

        val url = server.takeRequest().url
        assertEquals("/v1/taxa/autocomplete", url.encodedPath)
        assertEquals("island fox", url.queryParameter("q"))
        assertEquals(ANIMALIA_TAXON_ID, url.queryParameter("taxon_id"))
        assertEquals("en", url.queryParameter("locale"))
    }

    @Test
    fun searchAnimals_sendsUserAgent() = runBlocking {
        server.enqueue(jsonResponse(fixture("autocomplete_island_fox.json")))

        repository.searchAnimals("fox")

        assertEquals(USER_AGENT, server.takeRequest().headers["User-Agent"])
    }

    @Test
    fun searchAnimals_returnsCommonNamesInTheirOrder() = runBlocking {
        server.enqueue(jsonResponse(fixture("autocomplete_island_fox.json")))

        assertEquals(
            listOf("Island Fox", "Gray Foxes", "Santa Cruz Island Fox"),
            repository.searchAnimals("island fox")
        )
    }

    @Test
    fun searchAnimals_animalWithoutCommonName_returnsScientificName() = runBlocking {
        server.enqueue(jsonResponse(autocompleteOf(taxon(id = 1, scientificName = "Urocyon"))))

        assertEquals(listOf("Urocyon"), repository.searchAnimals("urocyon"))
    }

    @Test
    fun searchAnimals_sameNameTwice_returnsItOnce() = runBlocking {
        server.enqueue(
            jsonResponse(
                autocompleteOf(
                    taxon(
                        id = 1,
                        scientificName = "Larus occidentalis",
                        commonName = "Western Gull"
                    ),
                    taxon(id = 2, scientificName = "Larus wymani", commonName = "western gull")
                )
            )
        )

        assertEquals(listOf("Western Gull"), repository.searchAnimals("western gull"))
    }

    @Test
    fun searchAnimals_blankQuery_findsNothingWithoutAsking() = runBlocking {
        assertEquals(emptyList<String>(), repository.searchAnimals("  "))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun searchAnimals_serverError_throws() {
        server.enqueue(MockResponse.Builder().code(HTTP_SERVER_ERROR).build())

        assertThrows(AnimalLookupException::class.java) {
            runBlocking { repository.searchAnimals("fox") }
        }
    }

    @Test
    fun searchAnimals_unreadableResponse_throws() {
        server.enqueue(jsonResponse("""{"results": "not a list"}"""))

        assertThrows(AnimalLookupException::class.java) {
            runBlocking { repository.searchAnimals("fox") }
        }
    }

    @Test
    fun searchAnimals_serverUnreachable_throws() {
        server.close()

        assertThrows(AnimalLookupException::class.java) {
            runBlocking { repository.searchAnimals("fox") }
        }
    }

    @Test
    fun getAnimal_readsTheTaxonWhoseCommonNameMatches() = runBlocking {
        enqueueIslandFox()

        repository.getAnimal("island fox")

        server.takeRequest()
        assertEquals("/v1/taxa/$ISLAND_FOX_TAXON_ID", server.takeRequest().url.encodedPath)
    }

    @Test
    fun getAnimal_returnsWhatTheTaxonSays() = runBlocking {
        enqueueIslandFox()

        val animal = checkNotNull(repository.getAnimal(" Island fox "))

        assertEquals("Island Fox", animal.name)
        assertEquals("Urocyon littoralis", animal.scientificName)
        assertEquals(4232, animal.observationCount)
        assertEquals(ConservationStatus.NEAR_THREATENED, animal.conservationStatus)
        assertEquals("https://www.inaturalist.org/taxa/$ISLAND_FOX_TAXON_ID", animal.pageUrl)
    }

    @Test
    fun getAnimal_returnsClassOrderFamilyAndGenus() = runBlocking {
        enqueueIslandFox()

        val animal = checkNotNull(repository.getAnimal("Island Fox"))

        assertEquals(listOf("Mammalia", "Carnivora", "Canidae", "Urocyon"), animal.taxonomy)
    }

    @Test
    fun getAnimal_returnsPhotoWithAttributionAndWikipediaSummary() = runBlocking {
        enqueueIslandFox()

        val animal = checkNotNull(repository.getAnimal("Island Fox"))

        assertEquals(
            AnimalPhoto(
                url = "https://inaturalist-open-data.s3.amazonaws.com/photos/13274801/medium.jpg",
                attribution = "(c) Kevin Schafer, some rights reserved (CC BY-NC-ND), " +
                    "uploaded by Kevin Schafer"
            ),
            animal.photo
        )
        assertEquals("https://en.wikipedia.org/wiki/Urocyon%20littoralis", animal.wikipediaUrl)
        assertTrue(animal.wikipediaSummary.startsWith("The <b>island fox</b>"))
    }

    @Test
    fun getAnimal_matchesScientificName() = runBlocking {
        enqueueIslandFox()

        assertEquals("Island Fox", repository.getAnimal("urocyon littoralis")?.name)
    }

    @Test
    fun getAnimal_taxonWithoutDetails_returnsAnimalWithout() = runBlocking {
        server.enqueue(jsonResponse(autocompleteOf(taxon(id = 7, scientificName = "Urocyon"))))
        server.enqueue(jsonResponse(autocompleteOf(taxon(id = 7, scientificName = "Urocyon"))))

        val animal = checkNotNull(repository.getAnimal("Urocyon"))

        assertEquals(
            Animal(name = "Urocyon", scientificName = "Urocyon", pageUrl = taxonPage(7)),
            animal
        )
    }

    @Test
    fun getAnimal_blankName_findsNothingWithoutAsking() = runBlocking {
        assertNull(repository.getAnimal(" "))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun getAnimal_noExactMatch_findsNothingWithoutReadingTheTaxon() = runBlocking {
        server.enqueue(jsonResponse(fixture("autocomplete_island_fox.json")))

        assertNull(repository.getAnimal("fox"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun getAnimal_taxonUnreadable_throws() {
        server.enqueue(jsonResponse(fixture("autocomplete_island_fox.json")))
        server.enqueue(MockResponse.Builder().code(HTTP_SERVER_ERROR).build())

        assertThrows(AnimalLookupException::class.java) {
            runBlocking { repository.getAnimal("Island Fox") }
        }
    }

    private fun enqueueIslandFox() {
        server.enqueue(jsonResponse(fixture("autocomplete_island_fox.json")))
        server.enqueue(jsonResponse(fixture("taxon_island_fox.json")))
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/inaturalist/$name")) { "Missing fixture $name" }
            .readText()

    private fun jsonResponse(body: String): MockResponse = MockResponse.Builder()
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun autocompleteOf(vararg taxa: String): String =
        """{"total_results": ${taxa.size}, "results": [${taxa.joinToString()}]}"""

    private fun taxon(id: Int, scientificName: String, commonName: String? = null): String {
        val commonNameField = commonName?.let { ""","preferred_common_name": "$it"""" }.orEmpty()
        return """{"id": $id, "name": "$scientificName"$commonNameField}"""
    }

    private fun taxonPage(id: Int) = "https://www.inaturalist.org/taxa/$id"
}
