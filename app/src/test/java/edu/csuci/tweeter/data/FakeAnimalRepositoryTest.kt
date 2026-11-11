package edu.csuci.tweeter.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeAnimalRepositoryTest {
    private val repository = FakeAnimalRepository(
        animals = listOf("Red-tailed hawk", "Island fox", "Cooper's hawk", "Hawk moth")
    )

    @Test
    fun searchAnimals_blankQuery_findsNothing() = runBlocking {
        assertEquals(emptyList<String>(), repository.searchAnimals("  "))
    }

    @Test
    fun searchAnimals_noMatch_findsNothing() = runBlocking {
        assertEquals(emptyList<String>(), repository.searchAnimals("whale"))
    }

    @Test
    fun searchAnimals_matchesAnyPartOfTheName() = runBlocking {
        assertEquals(listOf("Island fox"), repository.searchAnimals("fox"))
    }

    @Test
    fun searchAnimals_ignoresCaseAndSurroundingSpaces() = runBlocking {
        assertEquals(listOf("Island fox"), repository.searchAnimals(" ISLAND FOX "))
    }

    @Test
    fun searchAnimals_namesStartingWithQueryComeFirst() = runBlocking {
        assertEquals(
            listOf("Hawk moth", "Red-tailed hawk", "Cooper's hawk"),
            repository.searchAnimals("hawk")
        )
    }
}
