package edu.csuci.tweeter.data

// A page size that fits every post a test shares, so that each lookup reads one page.
private const val ALL_IN_ONE_PAGE = Int.MAX_VALUE

/**
 * A [PostLookup] for tests, looking up [posts] in memory, [pageSize] posts at a time, as the
 * server's pages hold them. While [failure] is set, every lookup of posts throws it, and while
 * [countFailure] is set, every count does, as a lookup without a connection would.
 */
class FakePostLookup(var posts: List<Post> = emptyList()) : PostLookup {
    /** What every lookup of posts throws, or null while they succeed. */
    var failure: PostLookupException? = null

    /** What every count throws, or null while counts succeed. */
    var countFailure: PostLookupException? = null

    /** The most posts each page of [postsReceivedAfter] and [postsWithLikesAtLeast] holds. */
    var pageSize: Int = ALL_IN_ONE_PAGE

    /** The animals [countPostsOfAnimal] was asked about, in order. */
    val countedAnimals = mutableListOf<String>()

    /** The `maxCount` of each call to [countPostsOfAnimal], in order. */
    val maxCounts = mutableListOf<Int>()

    /**
     * The IDs of the posts [postsReceivedAfter] and [postsWithLikesAtLeast] read, in order, which
     * the server would charge a read each for.
     */
    val readPostIds = mutableListOf<String>()

    override suspend fun newestPostMillis(): Long? {
        failure?.let { throw it }
        return posts.maxOfOrNull { it.timestamp.toEpochMilli() }
    }

    override suspend fun postsReceivedAfter(timeMillis: Long): PostTable {
        failure?.let { throw it }
        val newPosts = posts.filter { it.timestamp.toEpochMilli() > timeMillis }
            .sortedByDescending { it.timestamp }
        return readInPages(newPosts)
    }

    override suspend fun countPostsOfAnimal(animal: String, maxCount: Int): Int {
        countFailure?.let { throw it }
        countedAnimals += animal
        maxCounts += maxCount
        return posts.count { it.animalType == animal }.coerceAtMost(maxCount)
    }

    override suspend fun holdsPost(postId: String): Boolean {
        failure?.let { throw it }
        return posts.any { it.id == postId }
    }

    override suspend fun postsWithLikesAtLeast(minLikeCount: Int): PostTable {
        failure?.let { throw it }
        val likedPosts = posts.filter { it.likeCount >= minLikeCount }
            .sortedByDescending { it.likeCount }
        return readInPages(likedPosts)
    }

    // Reads [matches], in their order, a page at a time, as the server would.
    private suspend fun readInPages(matches: List<Post>): PostTable {
        val pagedPosts = readEveryPage<Post>(pageSize) { lastPost ->
            val start = if (lastPost == null) 0 else matches.indexOf(lastPost) + 1
            matches.drop(start).take(pageSize)
        }
        pagedPosts.mapTo(readPostIds) { it.id }
        return PostTable.of(pagedPosts)
    }
}
