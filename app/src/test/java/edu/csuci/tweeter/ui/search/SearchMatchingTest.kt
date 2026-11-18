package edu.csuci.tweeter.ui.search

import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchMatchingTest {
    @Test
    fun filterByName_keepsNamesContainingQueryIgnoringCaseAndSpaces() {
        val names = listOf("Island fox", "Coyote", "Fox sparrow")

        assertEquals(listOf("Fox sparrow", "Island fox"), names.filterByName(" FOX ") { it })
    }

    @Test
    fun filterByName_namesStartingWithQuery_comeFirstThenAlphabetically() {
        val names = listOf("Sam", "alex", "Max", "Alma")

        assertEquals(listOf("alex", "Alma", "Max", "Sam"), names.filterByName("a") { it })
    }

    @Test
    fun filterByName_blankQuery_matchesNothing() {
        assertEquals(emptyList<String>(), listOf("Coyote").filterByName("  ") { it })
    }

    @Test
    fun postedAnimalsIn_countsPostsOfEachAnimalAlphabetically() {
        val posts = postsOfAnimals("Coyote", "Island fox", "Coyote")

        assertEquals(
            listOf(
                PostedAnimal("Coyote", postCount = 2),
                PostedAnimal("Island fox", postCount = 1)
            ),
            postedAnimalsIn(posts)
        )
    }

    @Test
    fun postedAnimalsIn_namesDifferingInCaseOrSpaces_areOneAnimalSpeltAsNewestPost() {
        val posts = postsOfAnimals("Island Fox ", "island fox")

        assertEquals(listOf(PostedAnimal("Island Fox", postCount = 2)), postedAnimalsIn(posts))
    }

    @Test
    fun postedAnimalsIn_postsNamingNoAnimal_areLeftOut() {
        assertEquals(emptyList<PostedAnimal>(), postedAnimalsIn(postsOfAnimals("", " ")))
    }

    // A table of one post per animal in [animals], newest first.
    private fun postsOfAnimals(vararg animals: String): PostTable = PostTable.of(
        animals.mapIndexed { index, animal ->
            SAMPLE_POSTS.first().copy(id = "post-$index", animalType = animal)
        }
    )
}
