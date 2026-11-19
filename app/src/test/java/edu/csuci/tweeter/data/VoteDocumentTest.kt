package edu.csuci.tweeter.data

import edu.csuci.tweeter.data.VoteDocument.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val POSSUM = "Possum"
private const val ROUND = 4

class VoteDocumentTest {
    private val vote = AnimalVote(POSSUM, ROUND)

    @Test
    fun toVote_fieldsOfVote_returnsSameVote() {
        val fields = VoteDocument.fieldsOf(vote)

        assertEquals(vote, VoteDocument.toVote(Field.entries.readFrom(fields)))
    }

    @Test
    fun toVote_roundStoredAsLong_readsIt() {
        // Firestore reads every whole number back as a Long.
        val fields = mapOf("animal" to POSSUM, "round" to ROUND.toLong())

        assertEquals(vote, VoteDocument.toVote(Field.entries.readFrom(fields)))
    }

    @Test
    fun toVote_missingAnimal_returnsNull() {
        val fields = VoteDocument.fieldsOf(vote) - "animal"

        assertNull(VoteDocument.toVote(Field.entries.readFrom(fields)))
    }

    @Test
    fun toVote_roundIsText_returnsNull() {
        val fields = VoteDocument.fieldsOf(vote) + ("round" to "4")

        assertNull(VoteDocument.toVote(Field.entries.readFrom(fields)))
    }
}
