package edu.csuci.tweeter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PostTest {
    @Test
    fun create_confidenceInRange_keepsIt() {
        val confidences = Post.MIN_CONFIDENCE..Post.MAX_CONFIDENCE

        assertEquals(confidences.toList(), confidences.map { postWithConfidence(it).confidence })
    }

    @Test(expected = IllegalArgumentException::class)
    fun create_confidenceBelowRange_throws() {
        postWithConfidence(Post.MIN_CONFIDENCE - 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun create_confidenceAboveRange_throws() {
        postWithConfidence(Post.MAX_CONFIDENCE + 1)
    }

    private fun postWithConfidence(confidence: Int): Post =
        SAMPLE_POSTS.first().copy(confidence = confidence)
}
