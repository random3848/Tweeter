package edu.csuci.tweeter.data

/**
 * A [PostLookup] for tests, looking up [posts] in memory. While [failure] is set, every lookup of
 * posts throws it, and while [countFailure] is set, every count does, as a lookup without a
 * connection would.
 */
class FakePostLookup(var posts: List<Post> = emptyList()) : PostLookup {
    /** What every lookup of posts throws, or null while they succeed. */
    var failure: PostLookupException? = null

    /** What every count throws, or null while counts succeed. */
    var countFailure: PostLookupException? = null

    /** The animals [countPostsOfAnimal] was asked about, in order. */
    val countedAnimals = mutableListOf<String>()

    /** The `maxCount` of each call to [countPostsOfAnimal], in order. */
    val maxCounts = mutableListOf<Int>()

    override suspend fun newestPostMillis(): Long? {
        failure?.let { throw it }
        return posts.maxOfOrNull { it.timestamp.toEpochMilli() }
    }

    override suspend fun postsReceivedAfter(timeMillis: Long): PostTable {
        failure?.let { throw it }
        val newPosts = posts.filter { it.timestamp.toEpochMilli() > timeMillis }
        return PostTable.of(newPosts.sortedByDescending { it.timestamp })
    }

    override suspend fun countPostsOfAnimal(animal: String, maxCount: Int): Int {
        countFailure?.let { throw it }
        countedAnimals += animal
        maxCounts += maxCount
        return posts.count { it.animalType == animal }.coerceAtMost(maxCount)
    }
}
