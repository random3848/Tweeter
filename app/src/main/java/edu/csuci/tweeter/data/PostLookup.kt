package edu.csuci.tweeter.data

/**
 * Looks posts up on the server once, for work that runs while no screen is open, such as the
 * checks for posts to notify the user of. Screens read posts from a [PostRepository] instead,
 * which follows them as they change.
 *
 * Times are the server's (in milliseconds since the epoch, as [PostTable.timestampMillis] gives
 * them), never the device's, whose clock may be wrong.
 *
 * The app uses [FirestorePostLookup].
 */
interface PostLookup {
    /**
     * When the server received its newest post, or null if it holds no posts.
     *
     * @throws PostLookupException If the server can't be reached or refuses the lookup.
     */
    suspend fun newestPostMillis(): Long?

    /**
     * Every post the server received after the millisecond [timeMillis], newest first, however
     * many there are: the lookup may read them in pages, but returns them all.
     *
     * @throws PostLookupException If the server can't be reached or refuses the lookup.
     */
    suspend fun postsReceivedAfter(timeMillis: Long): PostTable

    /**
     * How many posts name [animal] as their [Post.animalType], spelled exactly as it is, counting
     * no further than [maxCount], so that the count of a common animal stays cheap.
     *
     * @throws PostLookupException If the server can't be reached or refuses the lookup.
     */
    suspend fun countPostsOfAnimal(animal: String, maxCount: Int): Int
}
