package edu.csuci.tweeter.data

/** An [AnimalRarity] for tests, for which the animals in [rareAnimals] are rare. */
class FakeAnimalRarity(private val rareAnimals: Set<String>) : AnimalRarity {
    /** The animals [isRare] was asked about, in order. */
    val askedAnimals = mutableListOf<String>()

    override suspend fun isRare(animal: String): Boolean {
        askedAnimals += animal
        return animal in rareAnimals
    }
}
