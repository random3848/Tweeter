package edu.csuci.tweeter.data

/**
 * An [AnimalRepository] that searches a fixed list in memory, so Post can suggest animals before
 * the iNaturalist one is built.
 *
 * Names starting with the query rank above names that only contain it; otherwise the list's order
 * is kept.
 *
 * @param animals The names it searches.
 */
class FakeAnimalRepository(private val animals: List<String> = SAMPLE_ANIMALS) : AnimalRepository {
    override suspend fun searchAnimals(query: String): List<String> {
        val text = query.trim()
        if (text.isEmpty()) {
            return emptyList()
        }

        val matches = animals.filter { it.contains(text, ignoreCase = true) }
        return matches.sortedByDescending { it.startsWith(text, ignoreCase = true) }
    }
}

/**
 * Animals [FakeAnimalRepository] searches by default: ones people see around CSU Channel Islands.
 * Add or change them freely; nothing depends on the exact list.
 */
val SAMPLE_ANIMALS: List<String> = listOf(
    "Anna's hummingbird",
    "Bobcat",
    "Brown pelican",
    "California ground squirrel",
    "California scrub-jay",
    "California sea lion",
    "Coyote",
    "Desert cottontail",
    "Gopher snake",
    "Great blue heron",
    "Harbor seal",
    "Island fox",
    "Monarch butterfly",
    "Mule deer",
    "North American river otter",
    "Orb weaver",
    "Red-tailed hawk",
    "Snowy egret",
    "Western fence lizard",
    "Western gull"
)
