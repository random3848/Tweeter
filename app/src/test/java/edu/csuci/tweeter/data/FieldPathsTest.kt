package edu.csuci.tweeter.data

import com.google.firebase.firestore.FieldPath
import org.junit.Assert.assertEquals
import org.junit.Test

class FieldPathsTest {
    @Test
    fun fieldPathsOf_fields_returnsPathOfEachKeyInOrder() {
        val paths = fieldPathsOf(ProfileDocument.Field.entries)

        assertEquals(
            listOf(FieldPath.of("displayName"), FieldPath.of("bio"), FieldPath.of("photoUrl")),
            paths.toList()
        )
    }
}
