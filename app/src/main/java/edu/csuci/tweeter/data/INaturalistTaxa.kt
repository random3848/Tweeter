package edu.csuci.tweeter.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The ranks of an animal's ancestors that Animal.taxonomy names, broadest first.
private val TAXONOMY_RANKS = setOf("class", "order", "family", "genus")

// Where each taxon's page is on iNaturalist's website, followed by its ID.
private const val TAXON_PAGE_URL = "https://www.inaturalist.org/taxa/"

/**
 * The fields of iNaturalist's answers to `/v1/taxa/autocomplete` and `/v1/taxa/{id}` that the app
 * reads, as JSON names them. iNaturalist may leave out or send null for any field but the ID and
 * name.
 *
 * @property results The taxa found.
 */
@Serializable
// iNaturalist's name starts with a one-letter word.
@Suppress("ktlint:android-style:camel-case")
internal class INaturalistTaxa(val results: List<Taxon>) {
    /**
     * One taxon: a species, or a group such as a genus.
     *
     * @property id iNaturalist's ID of the taxon.
     * @property name Its scientific name.
     */
    @Serializable
    internal class Taxon(
        val id: Int,
        val name: String,
        @SerialName("preferred_common_name") val commonName: String? = null,
        val rank: String? = null,
        @SerialName("observations_count") val observationCount: Int? = null,
        @SerialName("wikipedia_url") val wikipediaUrl: String? = null,
        @SerialName("wikipedia_summary") val wikipediaSummary: String? = null,
        @SerialName("conservation_status") val conservationStatus: Status? = null,
        @SerialName("default_photo") val photo: Photo? = null,
        val ancestors: List<Taxon>? = null
    ) {
        /** The name posts tag the taxon with: its common name, or its scientific name. */
        val postedName: String
            get() = commonName?.takeIf { it.isNotBlank() } ?: name

        /** Whether [text] is the taxon's common or scientific name, ignoring case. */
        fun isNamed(text: String): Boolean =
            postedName.equals(text, ignoreCase = true) || name.equals(text, ignoreCase = true)

        /** What the app shows of the taxon. */
        fun toAnimal(): Animal = Animal(
            name = postedName,
            scientificName = name,
            photo = photo?.toAnimalPhoto(),
            wikipediaSummary = wikipediaSummary.orEmpty(),
            // iNaturalist leaves the spaces of the article's title in its URL.
            wikipediaUrl = wikipediaUrl?.replace(" ", "%20"),
            taxonomy = ancestors.orEmpty().filter { it.rank in TAXONOMY_RANKS }.map { it.name },
            conservationStatus = conservationStatus?.iucn?.let(ConservationStatus::fromIucnCode),
            observationCount = observationCount ?: 0,
            pageUrl = TAXON_PAGE_URL + id
        )
    }

    /**
     * How threatened a taxon is.
     *
     * @property iucn iNaturalist's number for the IUCN Red List category ([ConservationStatus]).
     */
    @Serializable
    internal class Status(val iucn: Int? = null)

    /**
     * A taxon's photo.
     *
     * @property url Where the medium-sized copy is, about 500 pixels wide.
     * @property attribution Who took it and under which license.
     */
    @Serializable
    internal class Photo(
        @SerialName("medium_url") val url: String? = null,
        val attribution: String? = null
    ) {
        /** The photo, or null when iNaturalist sent no URL. */
        fun toAnimalPhoto(): AnimalPhoto? =
            url?.let { AnimalPhoto(url = it, attribution = attribution.orEmpty()) }
    }
}
