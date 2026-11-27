package edu.csuci.tweeter.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.csuci.tweeter.HTTP_USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Learning tests of iNaturalist's API, called as the app calls it, over the network. They show
 * what changed if iNaturalist changes the fields the app reads.
 */
// iNaturalist's name starts with a one-letter word.
@Suppress("ktlint:android-style:camel-case")
@RunWith(AndroidJUnit4::class)
class INaturalistAnimalRepositoryLearningTest {
    private val repository = INaturalistAnimalRepository(
        baseUrl = "https://api.inaturalist.org",
        userAgent = HTTP_USER_AGENT,
        ioDispatcher = Dispatchers.IO
    )

    @Test
    fun searchAnimals_findsAnimalsByCommonName() = runBlocking {
        assertTrue("Island Fox" in repository.searchAnimals("island fox"))
    }

    @Test
    fun searchAnimals_leavesOutPlants() = runBlocking {
        val names = repository.searchAnimals("coast live oak")

        assertFalse(names.any { it.equals("Coast Live Oak", ignoreCase = true) })
    }

    @Test
    fun getAnimal_readsEveryFieldTheAnimalPageShows() = runBlocking {
        val animal = checkNotNull(repository.getAnimal("Island Fox"))

        assertEquals("Urocyon littoralis", animal.scientificName)
        assertEquals(listOf("Mammalia", "Carnivora", "Canidae", "Urocyon"), animal.taxonomy)
        assertNotNull(animal.photo)
        assertNotNull(animal.conservationStatus)
        assertNotNull(animal.wikipediaUrl)
        assertTrue(animal.wikipediaSummary.isNotEmpty())
        assertTrue(animal.observationCount > 0)
    }
}
