package edu.csuci.tweeter.data

import org.junit.Assert.assertTrue

/**
 * Asserts that [actual] holds the same posts as [expected], in the same rows. Tables are equal
 * only to themselves, so `assertEquals` can't compare them.
 */
fun assertSamePosts(expected: PostTable, actual: PostTable) {
    assertTrue("Expected $expected, but was $actual", expected.holdsSamePosts(actual))
}
