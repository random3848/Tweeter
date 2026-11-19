package edu.csuci.tweeter.notification

import edu.csuci.tweeter.data.FakeAnimalRarity
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.location.GeoPoint
import edu.csuci.tweeter.location.distanceMetersTo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

private const val USER_UID = "maya"
private const val OTHER_UID = "jordan"
private const val RARE_ANIMAL = "Bobcat"
private const val COMMON_ANIMAL = "Western fence lizard"
private const val RADIUS_METERS = 10_000.0

// A hundredth of a degree of latitude is about 1.1 km.
private const val NEARBY_DEGREES = 0.01
private const val FAR_DEGREES = 1.0

private val HOME = GeoPoint(latitude = 34.1626, longitude = -119.0434)

class NearbyRareAnimalSelectorTest {
    private val rarity = FakeAnimalRarity(rareAnimals = setOf(RARE_ANIMAL))
    private val selector = NearbyRareAnimalSelector(rarity, radiusMeters = RADIUS_METERS)
    private val recipient = Recipient(uid = USER_UID, location = HOME, notifiedPostIds = setOf())

    @Test
    fun rowsToNotify_rareAnimalNearbyByOtherUser_selectsIt() = runTest {
        val posts = tableOf(sighting(id = "rare"))

        assertArrayEquals(intArrayOf(0), selector.rowsToNotify(posts, recipient))
    }

    @Test
    fun rowsToNotify_commonAnimal_selectsNothing() = runTest {
        val posts = tableOf(sighting(id = "common", animal = COMMON_ANIMAL))

        assertArrayEquals(intArrayOf(), selector.rowsToNotify(posts, recipient))
    }

    @Test
    fun rowsToNotify_farAway_selectsNothing() = runTest {
        val posts = tableOf(sighting(id = "far", latitude = HOME.latitude + FAR_DEGREES))

        assertArrayEquals(intArrayOf(), selector.rowsToNotify(posts, recipient))
    }

    @Test
    fun rowsToNotify_exactlyAtRadius_selectsIt() = runTest {
        val edge = GeoPoint(HOME.latitude + NEARBY_DEGREES, HOME.longitude)
        val selectorToEdge = NearbyRareAnimalSelector(rarity, HOME.distanceMetersTo(edge))
        val posts = tableOf(sighting(id = "edge", latitude = edge.latitude))

        assertArrayEquals(intArrayOf(0), selectorToEdge.rowsToNotify(posts, recipient))
    }

    @Test
    fun rowsToNotify_justPastRadius_selectsNothing() = runTest {
        val edge = GeoPoint(HOME.latitude + NEARBY_DEGREES, HOME.longitude)
        val selectorShortOfEdge = NearbyRareAnimalSelector(rarity, HOME.distanceMetersTo(edge) - 1)
        val posts = tableOf(sighting(id = "past", latitude = edge.latitude))

        assertArrayEquals(intArrayOf(), selectorShortOfEdge.rowsToNotify(posts, recipient))
    }

    @Test
    fun rowsToNotify_ownPost_selectsNothing() = runTest {
        val posts = tableOf(sighting(id = "mine", authorUid = USER_UID))

        assertArrayEquals(intArrayOf(), selector.rowsToNotify(posts, recipient))
    }

    @Test
    fun rowsToNotify_postWithoutAnimal_selectsNothing() = runTest {
        val posts =
            tableOf(sighting(id = "empty", animal = ""), sighting(id = "blank", animal = " "))

        assertArrayEquals(intArrayOf(), selector.rowsToNotify(posts, recipient))
    }

    @Test
    fun rowsToNotify_severalPosts_selectsQualifyingRowsInTableOrder() = runTest {
        val posts = tableOf(
            sighting(id = "rare-1"),
            sighting(id = "common", animal = COMMON_ANIMAL),
            sighting(id = "mine", authorUid = USER_UID),
            sighting(id = "rare-2")
        )

        assertArrayEquals(intArrayOf(0, 3), selector.rowsToNotify(posts, recipient))
    }

    @Test
    fun rowsToNotify_asksRarityOnlyAboutNearbyPostsByOthers() = runTest {
        val posts = tableOf(
            sighting(id = "far", animal = COMMON_ANIMAL, latitude = HOME.latitude + FAR_DEGREES),
            sighting(id = "mine", animal = COMMON_ANIMAL, authorUid = USER_UID),
            sighting(id = "near")
        )

        selector.rowsToNotify(posts, recipient)

        assertEquals(listOf(RARE_ANIMAL), rarity.askedAnimals)
    }

    @Test
    fun rowsToNotify_alreadyNotified_selectsNothingAndAsksNothing() = runTest {
        val notifiedRecipient = recipient.copy(notifiedPostIds = setOf("seen"))
        val posts = tableOf(sighting(id = "seen"))

        val rows = selector.rowsToNotify(posts, notifiedRecipient)

        assertArrayEquals(intArrayOf(), rows)
        assertEquals(emptyList<String>(), rarity.askedAnimals)
    }

    @Test
    fun rowsToNotify_severalPostsOfOneAnimal_asksRarityOnceAndSelectsEach() = runTest {
        val posts = tableOf(
            sighting(id = "rare-1"),
            sighting(id = "common-1", animal = COMMON_ANIMAL),
            sighting(id = "rare-2"),
            sighting(id = "common-2", animal = COMMON_ANIMAL)
        )

        val rows = selector.rowsToNotify(posts, recipient)

        assertArrayEquals(intArrayOf(0, 2), rows)
        assertEquals(listOf(RARE_ANIMAL, COMMON_ANIMAL), rarity.askedAnimals)
    }

    private fun tableOf(vararg posts: Post): PostTable = PostTable.of(posts.toList())

    private fun sighting(
        id: String,
        animal: String = RARE_ANIMAL,
        authorUid: String = OTHER_UID,
        latitude: Double = HOME.latitude + NEARBY_DEGREES
    ): Post = SAMPLE_POSTS.first().copy(
        id = id,
        authorUid = authorUid,
        animalType = animal,
        latitude = latitude,
        longitude = HOME.longitude
    )
}
