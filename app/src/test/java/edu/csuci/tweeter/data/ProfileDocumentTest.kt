package edu.csuci.tweeter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileDocumentTest {
    @Test
    fun toProfile_noDocument_defaultProfile() {
        assertEquals(UserProfile.createDefault("uid"), ProfileDocument.toProfile("uid", null))
    }

    @Test
    fun toProfile_allFields_readsThem() {
        val fields = mapOf(
            "displayName" to "Maya Kim",
            "bio" to "Spiders, mostly.",
            "photoUrl" to "https://example.com/maya.jpg"
        )

        assertEquals(
            UserProfile(
                uid = "uid",
                displayName = "Maya Kim",
                bio = "Spiders, mostly.",
                photoUrl = "https://example.com/maya.jpg"
            ),
            ProfileDocument.toProfile("uid", fields)
        )
    }

    @Test
    fun toProfile_missingFields_defaults() {
        val result = ProfileDocument.toProfile("uid", mapOf("displayName" to "Maya Kim"))

        assertEquals(
            UserProfile(uid = "uid", displayName = "Maya Kim", bio = "", photoUrl = ""),
            result
        )
    }

    @Test
    fun toProfile_blankName_defaultName() {
        val result = ProfileDocument.toProfile("uid", mapOf("displayName" to "  "))

        assertEquals(UserProfile.DEFAULT_DISPLAY_NAME, result.displayName)
    }

    @Test
    fun toProfile_wrongTypes_defaults() {
        val fields = mapOf("displayName" to 42L, "bio" to true, "photoUrl" to null)

        assertEquals(UserProfile.createDefault("uid"), ProfileDocument.toProfile("uid", fields))
    }
}
