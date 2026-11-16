package edu.csuci.tweeter.data

/**
 * Where the names of animals a post can be tagged with ([Post.animalType]) come from.
 *
 * Post searches it as the user types, so a post names its animal the same way every other post
 * does. The app uses [FakeAnimalRepository] until the iNaturalist one is built.
 */
interface AnimalRepository {
    /**
     * Names of animals that contain [query], ignoring case and the spaces around it, best
     * matches first. Empty for a blank query.
     */
    suspend fun searchAnimals(query: String): List<String>
}
