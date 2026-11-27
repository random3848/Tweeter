package edu.csuci.tweeter.data

/**
 * How threatened an animal is, in the IUCN Red List's categories, from least to most.
 *
 * @property iucnCode The number iNaturalist gives the category.
 */
enum class ConservationStatus(val iucnCode: Int) {
    LEAST_CONCERN(iucnCode = 10),
    NEAR_THREATENED(iucnCode = 20),
    VULNERABLE(iucnCode = 30),
    ENDANGERED(iucnCode = 40),
    CRITICALLY_ENDANGERED(iucnCode = 50),
    EXTINCT_IN_THE_WILD(iucnCode = 60),
    EXTINCT(iucnCode = 70);

    /** Finds statuses by iNaturalist's number for them. */
    companion object {
        /** The status iNaturalist numbers [iucnCode], or null for numbers outside the Red List. */
        fun fromIucnCode(iucnCode: Int): ConservationStatus? = entries.find {
            it.iucnCode == iucnCode
        }
    }
}
