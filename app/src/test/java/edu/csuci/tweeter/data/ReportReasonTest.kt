package edu.csuci.tweeter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportReasonTest {
    // firestore.rules accepts exactly these reasons; changing one means changing the rules too.
    @Test
    fun names_matchFirestoreRules() {
        assertEquals(
            listOf("SPAM", "HARASSMENT", "SEXUAL_CONTENT", "VIOLENCE", "OTHER"),
            ReportReason.entries.map { it.name }
        )
    }
}
