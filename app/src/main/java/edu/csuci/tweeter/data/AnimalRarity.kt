package edu.csuci.tweeter.data

/**
 * Which animals are rare, so that a post of one is news to users nearby.
 *
 * The app uses [PostCountRarity], which judges by the app's own posts, until a source that knows
 * how often each animal is seen, such as iNaturalist, is built; that work adds another
 * implementation and wires it in `AppContainer` instead.
 */
interface AnimalRarity {
    /**
     * Whether [animal], as a post names it ([Post.animalType]), is rare.
     *
     * @throws PostLookupException If what it judges by can't be read.
     */
    suspend fun isRare(animal: String): Boolean
}
