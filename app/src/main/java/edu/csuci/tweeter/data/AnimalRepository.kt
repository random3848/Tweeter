package edu.csuci.tweeter.data

/**
 * Where the animals a post can be tagged with ([Post.animalType]) come from, and what is known
 * about each.
 *
 * Post searches it as the user types, so a post names its animal the same way every other post
 * does, and an animal's page shows what [getAnimal] finds.
 */
interface AnimalRepository {
    /**
     * Names of animal species that match [query], best matches first, so that posts name their
     * animals at the level users vote on. Empty for a blank query.
     *
     * @throws AnimalLookupException If the animals can't be searched.
     */
    suspend fun searchAnimals(query: String): List<String>

    /**
     * The animal that posts tag with [name], matched ignoring case and the spaces around it, or
     * null when no animal has that name.
     *
     * @throws AnimalLookupException If the animal can't be looked up.
     */
    suspend fun getAnimal(name: String): Animal?
}
