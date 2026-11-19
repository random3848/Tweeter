package edu.csuci.tweeter.data

/** The most posts that may name an animal, the one being judged included, for it to be rare. */
private const val MAX_POSTS_OF_RARE_ANIMAL = 3

/**
 * An [AnimalRarity] that calls an animal rare when at most [maxPosts] posts name it, counted with
 * [postLookup], so that an animal the app's users have hardly ever posted is news. It counts only
 * up to one past [maxPosts], which is enough to tell.
 */
class PostCountRarity(
    private val postLookup: PostLookup,
    private val maxPosts: Int = MAX_POSTS_OF_RARE_ANIMAL
) : AnimalRarity {
    override suspend fun isRare(animal: String): Boolean =
        postLookup.countPostsOfAnimal(animal, maxCount = maxPosts + 1) <= maxPosts
}
