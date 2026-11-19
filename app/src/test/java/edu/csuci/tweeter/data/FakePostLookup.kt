package edu.csuci.tweeter.data

/**
 * A [PostLookup] for tests, looking up [posts] in memory. While [failure] is set, every lookup
 * throws it, as a lookup without a connection would.
 */
class FakePostLookup(var posts: List<Post> = emptyList()) : PostLookup {
    /** What every lookup throws, or null while lookups succeed. */
    var failure: PostLookupException? = null

    /** The animals [countPostsOfAnimal] was asked about, in order. */
    val countedAnimals = mutableListOf<String>()

    override suspend fun postsReceivedAfter(timeMillis: Long): PostTable {
        failure?.let { throw it }
        val newPosts = posts.filter { it.timestamp.toEpochMilli() > timeMillis }
        return PostTable.of(newPosts.sortedByDescending { it.timestamp })
    }

    override suspend fun countPostsOfAnimal(animal: String): Int {
        failure?.let { throw it }
        countedAnimals += animal
        return posts.count { it.animalType == animal }
    }
}
