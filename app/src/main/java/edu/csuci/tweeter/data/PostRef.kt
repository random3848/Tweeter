package edu.csuci.tweeter.data

// A row is a non-negative Int.
private const val ROW_BITS = 31
private const val ROW_MASK = (1L shl ROW_BITS) - 1

// With ROW_BITS, as many bits as a double holds exactly: 53.
private const val GENERATION_BITS = 22

/**
 * A row of one [PostTable], for keeping where the reference can outlive its table: the row's
 * index, with the table's generation packed into the bits above it.
 *
 * Map pins carry one, because MapLibre can still draw the pins of an earlier table after the posts
 * have changed. [PostTable.rowOf] returns null for a reference into another table instead of
 * reading whichever post that row now holds.
 */
@JvmInline
value class PostRef private constructor(private val bits: Long) {
    /** The generation of the table this reference points into. */
    internal val generation: Int
        get() = (bits ushr ROW_BITS).toInt()

    /** The row this reference points to. */
    internal val row: Int
        get() = (bits and ROW_MASK).toInt()

    /**
     * This reference as a whole number of at most 53 bits, which a JSON number, a double, holds
     * exactly.
     */
    fun toLong(): Long = bits

    companion object {
        /** The largest generation a reference holds. Later generations start again from 0. */
        internal const val MAX_GENERATION = (1 shl GENERATION_BITS) - 1

        /** Returns the reference to [row] of the table of [generation]. */
        internal fun of(generation: Int, row: Int): PostRef {
            require(generation in 0..MAX_GENERATION) { "Generation $generation is out of range" }
            require(row >= 0) { "Row $row is negative" }
            return PostRef((generation.toLong() shl ROW_BITS) or row.toLong())
        }

        /** Returns the reference that [toLong] turned into [bits]. */
        fun fromLong(bits: Long): PostRef = PostRef(bits)
    }
}
