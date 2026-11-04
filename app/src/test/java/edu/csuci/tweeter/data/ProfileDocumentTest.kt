package edu.csuci.tweeter.data

import edu.csuci.tweeter.data.ProfileDocument.Field
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileDocumentTest {
    private val profile = UserProfile(
        uid = "uid",
        displayName = "Maya Kim",
        bio = "Spiders, mostly.",
        photoUrl = "https://example.com/maya.jpg"
    )

    @Test
    fun fieldsOf_profile_storesEverythingButUid() {
        assertEquals(
            mapOf(
                "displayName" to "Maya Kim",
                "bio" to "Spiders, mostly.",
                "photoUrl" to "https://example.com/maya.jpg"
            ),
            ProfileDocument.fieldsOf(profile)
        )
    }

    @Test
    fun toProfile_fieldsOfProfile_returnsSameProfile() {
        val fields = ProfileDocument.fieldsOf(profile)

        assertEquals(
            profile,
            ProfileDocument.toProfile(profile.uid, Field.entries.readFrom(fields))
        )
    }

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
            ProfileDocument.toProfile("uid", Field.entries.readFrom(fields))
        )
    }

    @Test
    fun toProfile_missingFields_defaults() {
        val result = ProfileDocument.toProfile(
            "uid",
            Field.entries.readFrom(mapOf("displayName" to "Maya Kim"))
        )

        assertEquals(
            UserProfile(uid = "uid", displayName = "Maya Kim", bio = "", photoUrl = ""),
            result
        )
    }

    @Test
    fun toProfile_blankName_defaultName() {
        val fields = mapOf("displayName" to "  ")

        val result = ProfileDocument.toProfile("uid", Field.entries.readFrom(fields))

        assertEquals(UserProfile.DEFAULT_DISPLAY_NAME, result.displayName)
    }

    @Test
    fun toProfile_wrongTypes_defaults() {
        val fields = mapOf("displayName" to 42L, "bio" to true, "photoUrl" to null)

        assertEquals(
            UserProfile.createDefault("uid"),
            ProfileDocument.toProfile("uid", Field.entries.readFrom(fields))
        )
    }
}
