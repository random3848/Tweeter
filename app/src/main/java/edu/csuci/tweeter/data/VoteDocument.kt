package edu.csuci.tweeter.data

/**
 * How a user's [AnimalVote] is stored: as a Firestore document in the [COLLECTION] subcollection
 * of the user's profile document, named by the ID of the post it's on.
 *
 * A document is read field by field into an array in [Field] order, never as a whole map.
 */
object VoteDocument {
    /** The subcollection of a profile document that holds the user's votes. */
    const val COLLECTION = "votes"

    /** A stored field, whose ordinal is its index in the array [toVote] reads. */
    enum class Field(override val key: String) : DocumentField {
        /** The animal voted for. */
        ANIMAL("animal"),

        /** The post's voting round the vote was cast in. */
        ROUND("round")
    }

    /** Returns the fields to store for [vote]; its post's ID is the document ID. */
    fun fieldsOf(vote: AnimalVote): Map<String, Any> = mapOf(
        Field.ANIMAL.key to vote.animal,
        Field.ROUND.key to vote.round
    )

    /**
     * Returns the vote stored with [fields], indexed by [Field.ordinal], or null if a field is
     * missing or has the wrong type.
     */
    fun toVote(fields: Array<Any?>): AnimalVote? {
        val animal = fields[Field.ANIMAL.ordinal] as? String ?: return null
        // Firestore returns whole numbers as Longs.
        val round = (fields[Field.ROUND.ordinal] as? Number)?.toInt() ?: return null
        return AnimalVote(animal, round)
    }
}
