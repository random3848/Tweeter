package edu.csuci.tweeter.data

/**
 * Looks posts up on the server once, for work that runs while no screen is open, such as the
 * checks for posts to notify the user of. Screens read posts from a [PostRepository] instead,
 * which follows them as they change.
 *
 * The app uses [FirestorePostLookup].
 */
interface PostLookup {
    /**
     * The posts the server received after the millisecond [timeMillis] (in milliseconds since the
     * epoch, as [PostTable.timestampMillis] gives it), newest first. When many are, only the newest
     * of them.
     *
     * @throws PostLookupException If the server can't be reached or refuses the lookup.
     */
    suspend fun postsReceivedAfter(timeMillis: Long): PostTable

    /**
     * How many posts name [animal] as their [Post.animalType], spelled exactly as it is.
     *
     * @throws PostLookupException If the server can't be reached or refuses the lookup.
     */
    suspend fun countPostsOfAnimal(animal: String): Int
}
