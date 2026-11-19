package edu.csuci.tweeter.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ANIMAL = "Bobcat"
private const val OTHER_ANIMAL = "Western fence lizard"
private const val MAX_POSTS = 2

class PostCountRarityTest {
    @Test
    fun isRare_namedByOnePost_isTrue() = runTest {
        assertTrue(rarityOf(postsOf(ANIMAL)).isRare(ANIMAL))
    }

    @Test
    fun isRare_namedByMaxPosts_isTrue() = runTest {
        assertTrue(rarityOf(postsOf(ANIMAL, ANIMAL)).isRare(ANIMAL))
    }

    @Test
    fun isRare_namedByMorePostsThanMax_isFalse() = runTest {
        assertFalse(rarityOf(postsOf(ANIMAL, ANIMAL, ANIMAL)).isRare(ANIMAL))
    }

    @Test
    fun isRare_countsOnlyPostsOfThatAnimal() = runTest {
        val rarity = rarityOf(postsOf(ANIMAL, OTHER_ANIMAL, OTHER_ANIMAL, OTHER_ANIMAL))

        assertTrue(rarity.isRare(ANIMAL))
    }

    @Test
    fun isRare_countsOnlyOnePastMax() = runTest {
        val lookup = FakePostLookup(postsOf(ANIMAL, ANIMAL, ANIMAL, ANIMAL))

        val isRare = PostCountRarity(lookup, maxPosts = MAX_POSTS).isRare(ANIMAL)

        assertFalse(isRare)
        assertEquals(listOf(MAX_POSTS + 1), lookup.maxCounts)
    }

    @Test(expected = PostLookupException::class)
    fun isRare_lookupFails_throws() = runTest {
        val lookup = FakePostLookup(postsOf(ANIMAL))
        lookup.countFailure = PostLookupException(IllegalStateException("Offline"))

        PostCountRarity(lookup, maxPosts = MAX_POSTS).isRare(ANIMAL)
    }

    private fun rarityOf(posts: List<Post>): PostCountRarity =
        PostCountRarity(FakePostLookup(posts), maxPosts = MAX_POSTS)

    private fun postsOf(vararg animals: String): List<Post> = animals.mapIndexed { index, animal ->
        SAMPLE_POSTS.first().copy(id = "post-$index", animalType = animal)
    }
}
