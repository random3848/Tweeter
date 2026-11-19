package edu.csuci.tweeter.notification

import edu.csuci.tweeter.data.AnimalRarity
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.location.GeoPoint
import edu.csuci.tweeter.location.distanceMetersTo

// How far from the user a post may be for its animal to count as near them.
private const val NEARBY_RADIUS_METERS = 10_000.0

/**
 * Chooses the posts a user hears of as rare animals near them: posts by someone else, naming an
 * animal that [rarity] calls rare, at most [radiusMeters] from the user.
 */
class NearbyRareAnimalSelector(
    private val rarity: AnimalRarity,
    private val radiusMeters: Double = NEARBY_RADIUS_METERS
) {
    /**
     * The rows of [posts] that [recipient] is to be notified of, in table order. Asks [rarity]
     * only about the posts that pass every other test, since it may look the animal up online.
     *
     * @throws edu.csuci.tweeter.data.PostLookupException If [rarity] can't tell.
     */
    suspend fun rowsToNotify(posts: PostTable, recipient: Recipient): IntArray {
        val nearbySightings = (0 until posts.size).filter { row ->
            isNearbySighting(posts, row, recipient)
        }
        return nearbySightings.filter { row -> rarity.isRare(posts.animalType(row)) }.toIntArray()
    }

    // Whether the post in [row] names an animal and was shared near [recipient] by someone else.
    private fun isNearbySighting(posts: PostTable, row: Int, recipient: Recipient): Boolean {
        val namesAnimal = posts.animalType(row).isNotBlank()
        val isOwnPost = posts.authorUid(row) == recipient.uid
        return namesAnimal && !isOwnPost && isNear(posts, row, recipient.location)
    }

    private fun isNear(posts: PostTable, row: Int, location: GeoPoint): Boolean {
        val postLocation = GeoPoint(posts.latitude(row), posts.longitude(row))
        return location.distanceMetersTo(postLocation) <= radiusMeters
    }
}
