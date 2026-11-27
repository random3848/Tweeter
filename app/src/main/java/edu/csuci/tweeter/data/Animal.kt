package edu.csuci.tweeter.data

/**
 * What the app knows about one animal, for its page: names, a photo, where it sits in the tree of
 * life, and a short description.
 *
 * @property name The name posts tag it with ([Post.animalType]): its common name, or its
 *     scientific name if it has none.
 * @property scientificName Its scientific name, such as "Urocyon littoralis"; empty when unknown.
 * @property photo A photo of the animal, or null when there is none.
 * @property wikipediaSummary The opening of its Wikipedia article, which may hold `<b>` and `<i>`
 *     HTML tags; empty when there is none.
 * @property wikipediaUrl The Wikipedia article [wikipediaSummary] comes from, or null.
 * @property taxonomy The scientific names of its class, order, family and genus, broadest first,
 *     leaving out any it doesn't have.
 * @property conservationStatus How threatened it is, or null when that hasn't been assessed.
 * @property observationCount How many times people have recorded seeing it on iNaturalist.
 * @property pageUrl Its page on iNaturalist, or null when it has none.
 */
data class Animal(
    val name: String,
    val scientificName: String = "",
    val photo: AnimalPhoto? = null,
    val wikipediaSummary: String = "",
    val wikipediaUrl: String? = null,
    val taxonomy: List<String> = emptyList(),
    val conservationStatus: ConservationStatus? = null,
    val observationCount: Int = 0,
    val pageUrl: String? = null
)

/**
 * A photo of an animal, which must be shown with its [attribution] to honor its license.
 *
 * @property url Where the photo is, at a size that fills the width of a phone.
 * @property attribution Who took the photo and under which license, such as "(c) Kevin Schafer,
 *     some rights reserved (CC BY-NC-ND)".
 */
data class AnimalPhoto(val url: String, val attribution: String)
