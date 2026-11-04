package edu.csuci.tweeter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The largest whole number a JSON number (a double) holds exactly: 2^53 - 1.
private const val MAX_EXACT_JSON_INTEGER = (1L shl 53) - 1

private const val SOME_GENERATION = 7
private const val SOME_ROW = 42

class PostRefTest {
    @Test
    fun of_keepsGenerationAndRowApart() {
        val ref = PostRef.of(generation = SOME_GENERATION, row = SOME_ROW)

        assertEquals(SOME_GENERATION to SOME_ROW, ref.generation to ref.row)
    }

    @Test
    fun fromLong_ofToLong_returnsSameRef() {
        val ref = PostRef.of(generation = SOME_GENERATION, row = SOME_ROW)

        assertEquals(ref, PostRef.fromLong(ref.toLong()))
    }

    @Test
    fun toLong_largestGenerationAndRow_fitsInJsonNumberExactly() {
        val largest = PostRef.of(generation = PostRef.MAX_GENERATION, row = Int.MAX_VALUE)

        assertTrue(largest.toLong() <= MAX_EXACT_JSON_INTEGER)
    }

    @Test
    fun of_largestGenerationAndRow_keepsBoth() {
        val largest = PostRef.of(generation = PostRef.MAX_GENERATION, row = Int.MAX_VALUE)

        assertEquals(PostRef.MAX_GENERATION to Int.MAX_VALUE, largest.generation to largest.row)
    }
}
